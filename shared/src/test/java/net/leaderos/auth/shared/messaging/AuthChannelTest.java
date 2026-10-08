package net.leaderos.auth.shared.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthChannelTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AuthChannel.Key KEY = AuthChannel.Key.derive("correct horse battery staple");
    private static final AuthChannel.Key OTHER = AuthChannel.Key.derive("a completely different secret");
    private static final long NOW = 1_800_000_000_000L;

    @TempDir
    Path directory;

    @Test
    void signedStatusRoundTrips() {
        byte[] message = AuthChannel.status(KEY, "Steve", true, NOW, RANDOM);
        AuthChannel.Result result = AuthChannel.read(message, Collections.singletonList(KEY), false,
                new ReplayGuard(), NOW + 500);

        assertTrue(result.isAccepted());
        assertEquals(AuthChannel.STATUS, result.getMessage().getType());
        assertEquals("Steve", result.getMessage().getPlayer());
        assertTrue(result.getMessage().isAuthenticated());
        assertTrue(result.getMessage().isSigned());
    }

    @Test
    void signedConnectRoundTripsWithAnyConfiguredKey() {
        byte[] message = AuthChannel.connect(KEY, ".BedrockSteve", "survival", NOW, RANDOM);
        AuthChannel.Result result = AuthChannel.read(message, Arrays.asList(OTHER, KEY), false,
                new ReplayGuard(), NOW);

        assertTrue(result.isAccepted());
        assertEquals(AuthChannel.CONNECT, result.getMessage().getType());
        assertEquals(".BedrockSteve", result.getMessage().getPlayer());
        assertEquals("survival", result.getMessage().getServer());
    }

    @Test
    void wrongKeyIsRejected() {
        byte[] message = AuthChannel.status(OTHER, "Steve", true, NOW, RANDOM);
        AuthChannel.Result result = AuthChannel.read(message, Collections.singletonList(KEY), false,
                new ReplayGuard(), NOW);

        assertFalse(result.isAccepted());
        assertEquals(AuthChannel.Rejection.BAD_SIGNATURE, result.getRejection());
    }

    @Test
    void noKeysRejectsEverySignedMessage() {
        byte[] message = AuthChannel.status(KEY, "Steve", true, NOW, RANDOM);
        AuthChannel.Result result = AuthChannel.read(message, Collections.<AuthChannel.Key>emptyList(), false,
                new ReplayGuard(), NOW);

        assertEquals(AuthChannel.Rejection.BAD_SIGNATURE, result.getRejection());
    }

    @Test
    void everyFlippedBitIsDetected() {
        byte[] original = AuthChannel.status(KEY, "Steve", false, NOW, RANDOM);
        for (int index = 0; index < original.length; index++) {
            byte[] tampered = original.clone();
            tampered[index] ^= 0x01;
            AuthChannel.Result result = AuthChannel.read(tampered, Collections.singletonList(KEY), false,
                    new ReplayGuard(), NOW);
            assertFalse(result.isAccepted(), "flipping byte " + index + " must be detected");
        }
    }

    @Test
    void forgedAuthenticatedFlagIsRejected() {
        byte[] original = AuthChannel.status(KEY, "Steve", false, NOW, RANDOM);
        // The boolean sits right before the 32-byte MAC.
        byte[] forged = original.clone();
        forged[forged.length - 33] = 1;
        AuthChannel.Result result = AuthChannel.read(forged, Collections.singletonList(KEY), false,
                new ReplayGuard(), NOW);

        assertEquals(AuthChannel.Rejection.BAD_SIGNATURE, result.getRejection());
    }

    @Test
    void replayIsRejected() {
        ReplayGuard guard = new ReplayGuard();
        byte[] message = AuthChannel.status(KEY, "Steve", true, NOW, RANDOM);

        assertTrue(AuthChannel.read(message, Collections.singletonList(KEY), false, guard, NOW).isAccepted());
        AuthChannel.Result replay = AuthChannel.read(message, Collections.singletonList(KEY), false, guard,
                NOW + 1000);
        assertEquals(AuthChannel.Rejection.REPLAYED, replay.getRejection());
    }

    @Test
    void staleAndFutureMessagesAreRejected() {
        byte[] message = AuthChannel.status(KEY, "Steve", true, NOW, RANDOM);

        assertEquals(AuthChannel.Rejection.EXPIRED, AuthChannel.read(message, Collections.singletonList(KEY),
                false, new ReplayGuard(), NOW + AuthChannel.MAX_AGE_MILLIS + 1).getRejection());
        assertEquals(AuthChannel.Rejection.EXPIRED, AuthChannel.read(message, Collections.singletonList(KEY),
                false, new ReplayGuard(), NOW - AuthChannel.MAX_AGE_MILLIS - 1).getRejection());
    }

    @Test
    void unsignedMessagesNeedExplicitLegacyMode() {
        byte[] message = AuthChannel.status(null, "Steve", true, NOW, RANDOM);

        assertEquals(AuthChannel.Rejection.UNSIGNED, AuthChannel.read(message, Collections.singletonList(KEY),
                false, new ReplayGuard(), NOW).getRejection());
        AuthChannel.Result legacy = AuthChannel.read(message, Collections.singletonList(KEY), true,
                new ReplayGuard(), NOW);
        assertTrue(legacy.isAccepted());
        assertFalse(legacy.getMessage().isSigned());
    }

    @Test
    void malformedInputNeverThrows() {
        List<byte[]> inputs = Arrays.asList(null, new byte[0], new byte[] {'L'}, new byte[] {'L', 'A', 1, 1},
                new byte[600], "LA\u0001\u0009garbage".getBytes(StandardCharsets.UTF_8));
        for (byte[] input : inputs) {
            AuthChannel.Result result = AuthChannel.read(input, Collections.singletonList(KEY), true,
                    new ReplayGuard(), NOW);
            assertFalse(result.isAccepted());
        }
        for (int i = 0; i < 2000; i++) {
            byte[] random = new byte[RANDOM.nextInt(200)];
            RANDOM.nextBytes(random);
            if (random.length > 1) {
                random[0] = 'L';
                random[1] = 'A';
            }
            assertFalse(AuthChannel.read(random, Collections.singletonList(KEY), true, new ReplayGuard(), NOW)
                    .isAccepted());
        }
    }

    @Test
    void unknownTypeAndTrailingBytesAreRejected() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[] {'L', 'A', AuthChannel.VERSION_UNSIGNED, 9});
        out.writeUTF("Steve");
        out.writeLong(NOW);
        out.write(new byte[16]);
        out.writeBoolean(true);
        assertEquals(AuthChannel.Rejection.MALFORMED, AuthChannel.read(bytes.toByteArray(),
                Collections.singletonList(KEY), true, new ReplayGuard(), NOW).getRejection());

        byte[] valid = AuthChannel.status(null, "Steve", true, NOW, RANDOM);
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertEquals(AuthChannel.Rejection.MALFORMED, AuthChannel.read(trailing,
                Collections.singletonList(KEY), true, new ReplayGuard(), NOW).getRejection());
    }

    @Test
    void shortSecretsAndBadNamesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> AuthChannel.Key.derive("short"));
        assertThrows(IllegalArgumentException.class, () -> AuthChannel.Key.derive(null));
        assertThrows(IllegalArgumentException.class, () -> AuthChannel.status(KEY, "", true, NOW, RANDOM));
        assertThrows(IllegalArgumentException.class,
                () -> AuthChannel.connect(KEY, "Steve", "bad\nserver", NOW, RANDOM));
    }

    @Test
    void sameSecretDerivesSameKeyOnBothSides() {
        AuthChannel.Key backend = AuthChannel.Key.derive("  shared-secret-value-123  ");
        AuthChannel.Key proxy = AuthChannel.Key.derive("shared-secret-value-123");
        byte[] message = AuthChannel.status(backend, "Alex", true, NOW, RANDOM);
        assertTrue(AuthChannel.read(message, Collections.singletonList(proxy), false, new ReplayGuard(), NOW)
                .isAccepted());
    }

    @Test
    void replayGuardForgetsAfterRetentionAndStaysBounded() {
        ReplayGuard guard = new ReplayGuard(1000, 3);
        byte[] a = {1};
        assertTrue(guard.firstUse(a, 0));
        assertFalse(guard.firstUse(a, 999));
        assertTrue(guard.firstUse(a, 1001), "expired nonces are forgotten");
        for (int i = 2; i < 10; i++) {
            assertTrue(guard.firstUse(new byte[] {(byte) i}, 1001));
        }
        assertEquals(3, guard.size());
    }

    @Test
    void discoversVelocityForwardingSecret() throws Exception {
        Files.write(directory.resolve("velocity.toml"), Arrays.asList(
                "config-version = \"2.7\"",
                "player-info-forwarding-mode = \"modern\"",
                "forwarding-secret-file = \"forwarding.secret\"",
                "[servers]",
                "forwarding-secret-file = \"ignored.secret\""));
        Files.write(directory.resolve("forwarding.secret"), "velocity-forwarding-secret-xyz\n".getBytes(StandardCharsets.UTF_8));

        List<String> secrets = SecretDiscovery.velocity(directory, "");
        assertEquals(Collections.singletonList("velocity-forwarding-secret-xyz"), secrets);

        List<String> configuredFirst = SecretDiscovery.velocity(directory, "configured-secret-0123456");
        assertEquals("configured-secret-0123456", configuredFirst.get(0));
        assertEquals(2, SecretDiscovery.keys(configuredFirst).size());
    }

    @Test
    void ignoresVelocitySecretOutsideProxyRootOrWithLegacyForwarding() throws Exception {
        Files.write(directory.resolve("velocity.toml"), Arrays.asList(
                "player-info-forwarding-mode = \"modern\"",
                "forwarding-secret-file = \"../outside.secret\""));
        assertTrue(SecretDiscovery.velocity(directory, "").isEmpty());

        Files.write(directory.resolve("velocity.toml"), Arrays.asList(
                "player-info-forwarding-mode = \"legacy\""));
        Files.write(directory.resolve("forwarding.secret"), "velocity-forwarding-secret-xyz".getBytes(StandardCharsets.UTF_8));
        assertTrue(SecretDiscovery.velocity(directory, "short").isEmpty());
    }

    @Test
    void discoversBungeeGuardToken() throws Exception {
        Path folder = Files.createDirectories(directory.resolve("plugins").resolve("BungeeGuard"));
        Files.write(folder.resolve("token.yml"), Arrays.asList("# BungeeGuard", "token: 'AbCdEfGhIjKlMnOpQrStUvWxYz0123456789'"));

        assertEquals(Collections.singletonList("AbCdEfGhIjKlMnOpQrStUvWxYz0123456789"),
                SecretDiscovery.bungee(directory, ""));
        assertArrayEquals(new Object[0], SecretDiscovery.bungee(directory.resolve("missing"), "").toArray());
    }
}
