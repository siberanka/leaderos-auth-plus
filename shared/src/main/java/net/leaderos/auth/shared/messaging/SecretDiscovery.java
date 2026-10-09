package net.leaderos.auth.shared.messaging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the secret a proxy already shares with its backend servers, so proxy messaging can be
 * signed without extra setup: the configured secret first, then Velocity's forwarding secret
 * (modern or BungeeGuard forwarding) or BungeeGuard's token on BungeeCord. Secrets shorter than
 * {@link AuthChannel#MIN_SECRET_LENGTH} characters are ignored.
 */
public final class SecretDiscovery {

    private static final Pattern TOML = Pattern.compile("^\\s*([A-Za-z0-9_-]+)\\s*=\\s*\"([^\"]*)\"\\s*(#.*)?$");
    private static final Pattern YAML_TOKEN = Pattern.compile("^token\\s*:\\s*['\"]?([^'\"#\\s]+)['\"]?\\s*(#.*)?$");
    private static final long MAX_FILE = 64 * 1024;

    private SecretDiscovery() {
    }

    /**
     * Secrets usable on a Velocity proxy.
     *
     * @param proxyRoot  proxy working directory
     * @param configured secret from the plugin configuration, may be empty
     * @return secrets in priority order (first one signs)
     */
    public static List<String> velocity(Path proxyRoot, String configured) {
        List<String> secrets = new ArrayList<>();
        add(secrets, configured);
        Map<String, String> toml = topLevelToml(read(proxyRoot.resolve("velocity.toml")));
        String mode = toml.containsKey("player-info-forwarding-mode") ? toml.get("player-info-forwarding-mode") : "";
        if ("modern".equalsIgnoreCase(mode) || "bungeeguard".equalsIgnoreCase(mode)) {
            String file = toml.containsKey("forwarding-secret-file") ? toml.get("forwarding-secret-file")
                    : "forwarding.secret";
            Path root = proxyRoot.toAbsolutePath().normalize();
            Path secretFile = root.resolve(file).normalize();
            if (secretFile.startsWith(root)) {
                add(secrets, read(secretFile));
            }
            // Velocity before 3.1 kept the secret inline.
            add(secrets, toml.get("forwarding-secret"));
        }
        return secrets;
    }

    /**
     * Secrets usable on a BungeeCord proxy.
     *
     * @param proxyRoot  proxy working directory
     * @param configured secret from the plugin configuration, may be empty
     * @return secrets in priority order (first one signs)
     */
    public static List<String> bungee(Path proxyRoot, String configured) {
        List<String> secrets = new ArrayList<>();
        add(secrets, configured);
        String text = read(proxyRoot.resolve("plugins").resolve("BungeeGuard").resolve("token.yml"));
        if (text != null) {
            for (String line : text.split("\r?\n")) {
                Matcher matcher = YAML_TOKEN.matcher(line.trim());
                if (matcher.matches()) {
                    add(secrets, matcher.group(1));
                    break;
                }
            }
        }
        return secrets;
    }

    /**
     * Reads Velocity's player-info-forwarding-mode from velocity.toml.
     *
     * @param proxyRoot proxy working directory
     * @return the mode in lower case ("modern", "bungeeguard", "legacy", "none"), or "" when unknown
     */
    public static String velocityForwardingMode(Path proxyRoot) {
        Map<String, String> toml = topLevelToml(read(proxyRoot.resolve("velocity.toml")));
        String mode = toml.get("player-info-forwarding-mode");
        return mode == null ? "" : mode.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Derives keys for every usable secret.
     *
     * @param secrets candidate secrets
     * @return keys, possibly empty
     */
    public static List<AuthChannel.Key> keys(List<String> secrets) {
        List<AuthChannel.Key> keys = new ArrayList<>();
        for (String secret : secrets) {
            if (AuthChannel.isUsableSecret(secret)) {
                keys.add(AuthChannel.Key.derive(secret));
            }
        }
        return keys;
    }

    static Map<String, String> topLevelToml(String text) {
        Map<String, String> values = new HashMap<>();
        if (text == null) {
            return values;
        }
        for (String line : text.split("\r?\n")) {
            if (line.trim().startsWith("[")) {
                break;
            }
            Matcher matcher = TOML.matcher(line);
            if (matcher.matches()) {
                values.put(matcher.group(1), matcher.group(2));
            }
        }
        return values;
    }

    private static void add(List<String> secrets, String secret) {
        if (secret == null) {
            return;
        }
        String value = secret.trim();
        if (AuthChannel.isUsableSecret(value) && !secrets.contains(value)) {
            secrets.add(value);
        }
    }

    private static String read(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE) {
                return null;
            }
            try (InputStream in = Files.newInputStream(file)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (IOException | SecurityException unreadable) {
            return null;
        }
    }
}
