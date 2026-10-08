package net.leaderos.auth.shared.messaging;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

/**
 * Plugin-message protocol between LeaderOS Auth on a backend server and LeaderOS Auth on the proxy.
 *
 * <p>The backend tells the proxy that a player has (or has not) authenticated, and asks it to send
 * an authenticated player on to another server. Every message carries an HMAC-SHA256 over its
 * content with a key derived from a secret both sides share (an explicit setting, Velocity's
 * forwarding secret or a BungeeGuard token), a timestamp and a random single-use nonce. The proxy
 * consumes the channel in both directions, so clients can neither read, forge nor replay messages.
 * Anything malformed, unsigned, stale or replayed is rejected before it is acted on.</p>
 */
public final class AuthChannel {

    /** Lowercase namespaced channel, as modern servers require. */
    public static final String CHANNEL = "leaderos:auth";

    public static final byte VERSION_UNSIGNED = 0;
    public static final byte VERSION_SIGNED = 1;

    public static final byte STATUS = 1;
    public static final byte CONNECT = 2;

    /** No valid message is larger; bigger payloads are dropped unread. */
    public static final int MAX_MESSAGE = 512;
    /** Accepted clock difference between the backend and the proxy. */
    public static final long MAX_AGE_MILLIS = 60_000L;
    /** Shortest secret accepted for signing. */
    public static final int MIN_SECRET_LENGTH = 16;

    private static final int NONCE_BYTES = 16;
    private static final int MAC_BYTES = 32;
    private static final int MAX_NAME_LENGTH = 64;
    private static final byte[] MAGIC = {'L', 'A'};
    private static final byte[] KEY_LABEL = "leaderos-auth-proxy-messaging-v1".getBytes(StandardCharsets.UTF_8);

    private AuthChannel() {
    }

    /** Why a message was not accepted. */
    public enum Rejection {
        MALFORMED,
        UNSIGNED,
        BAD_SIGNATURE,
        EXPIRED,
        REPLAYED
    }

    /** A signing key derived from one shared secret. */
    public static final class Key {
        private final byte[] key;

        private Key(byte[] key) {
            this.key = key;
        }

        /**
         * Derives a key; secrets shorter than {@link #MIN_SECRET_LENGTH} characters are refused.
         *
         * @param secret shared secret
         * @return derived key
         */
        public static Key derive(String secret) {
            if (!isUsableSecret(secret)) {
                throw new IllegalArgumentException("secret too short");
            }
            return new Key(hmac(secret.trim().getBytes(StandardCharsets.UTF_8), KEY_LABEL));
        }

        byte[] mac(byte[] data, int length) {
            return hmac(key, data, length);
        }
    }

    /** One decoded message. */
    public static final class Message {
        private final byte type;
        private final boolean signed;
        private final String player;
        private final long timestamp;
        private final boolean authenticated;
        private final String server;

        private Message(byte type, boolean signed, String player, long timestamp, boolean authenticated,
                String server) {
            this.type = type;
            this.signed = signed;
            this.player = player;
            this.timestamp = timestamp;
            this.authenticated = authenticated;
            this.server = server;
        }

        public byte getType() {
            return type;
        }

        public boolean isSigned() {
            return signed;
        }

        public String getPlayer() {
            return player;
        }

        public long getTimestamp() {
            return timestamp;
        }

        /** Only meaningful for {@link #STATUS}. */
        public boolean isAuthenticated() {
            return authenticated;
        }

        /** Only meaningful for {@link #CONNECT}. */
        public String getServer() {
            return server;
        }
    }

    /** Outcome of {@link #read}. Exactly one of message and rejection is set. */
    public static final class Result {
        private final Message message;
        private final Rejection rejection;

        private Result(Message message, Rejection rejection) {
            this.message = message;
            this.rejection = rejection;
        }

        public boolean isAccepted() {
            return message != null;
        }

        public Message getMessage() {
            return message;
        }

        public Rejection getRejection() {
            return rejection;
        }
    }

    public static boolean isUsableSecret(String secret) {
        return secret != null && secret.trim().length() >= MIN_SECRET_LENGTH;
    }

    /**
     * Builds a status message.
     *
     * @param key           signing key, or null for an unsigned message (accepted only by proxies that allow it)
     * @param player        player name
     * @param authenticated whether the player is authenticated
     * @param now           current time in milliseconds
     * @param random        nonce source
     * @return encoded message
     */
    public static byte[] status(Key key, String player, boolean authenticated, long now, SecureRandom random) {
        return encode(key, STATUS, player, now, random, authenticated, null);
    }

    /**
     * Builds a connect request.
     *
     * @param key    signing key, or null for an unsigned message (accepted only by proxies that allow it)
     * @param player player name
     * @param server server the backend wants the player sent to
     * @param now    current time in milliseconds
     * @param random nonce source
     * @return encoded message
     */
    public static byte[] connect(Key key, String player, String server, long now, SecureRandom random) {
        return encode(key, CONNECT, player, now, random, false, checkName(server));
    }

    /**
     * Verifies and decodes one message. Never throws for malformed input.
     *
     * @param data          raw message
     * @param keys          keys to verify against (any one may match)
     * @param allowUnsigned accept {@link #VERSION_UNSIGNED} messages (legacy, insecure)
     * @param replayGuard   nonce store; a nonce is accepted once
     * @param now           current time in milliseconds
     * @return the decoded message or the reason it was rejected
     */
    public static Result read(byte[] data, List<Key> keys, boolean allowUnsigned, ReplayGuard replayGuard,
            long now) {
        if (data == null || data.length < MAGIC.length + 2 || data.length > MAX_MESSAGE
                || data[0] != MAGIC[0] || data[1] != MAGIC[1]) {
            return reject(Rejection.MALFORMED);
        }
        byte version = data[2];
        int body;
        if (version == VERSION_SIGNED) {
            if (data.length < MAGIC.length + 2 + MAC_BYTES) {
                return reject(Rejection.MALFORMED);
            }
            body = data.length - MAC_BYTES;
            byte[] tag = Arrays.copyOfRange(data, body, data.length);
            boolean valid = false;
            if (keys != null) {
                for (Key key : keys) {
                    // Compare against every key without short-circuiting.
                    valid |= MessageDigest.isEqual(key.mac(data, body), tag);
                }
            }
            if (!valid) {
                return reject(Rejection.BAD_SIGNATURE);
            }
        } else if (version == VERSION_UNSIGNED) {
            if (!allowUnsigned) {
                return reject(Rejection.UNSIGNED);
            }
            body = data.length;
        } else {
            return reject(Rejection.MALFORMED);
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 4, body - 4));
            byte type = data[3];
            String player = checkName(in.readUTF());
            long timestamp = in.readLong();
            byte[] nonce = new byte[NONCE_BYTES];
            in.readFully(nonce);
            boolean authenticated = false;
            String server = null;
            if (type == STATUS) {
                authenticated = in.readBoolean();
            } else if (type == CONNECT) {
                server = checkName(in.readUTF());
            } else {
                return reject(Rejection.MALFORMED);
            }
            if (in.available() != 0) {
                return reject(Rejection.MALFORMED);
            }
            if (Math.abs(now - timestamp) > MAX_AGE_MILLIS) {
                return reject(Rejection.EXPIRED);
            }
            if (replayGuard == null || !replayGuard.firstUse(nonce, now)) {
                return reject(Rejection.REPLAYED);
            }
            return new Result(new Message(type, version == VERSION_SIGNED, player, timestamp, authenticated, server),
                    null);
        } catch (IOException | IllegalArgumentException malformed) {
            return reject(Rejection.MALFORMED);
        }
    }

    private static byte[] encode(Key key, byte type, String player, long now, SecureRandom random,
            boolean authenticated, String server) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(96);
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(MAGIC);
            out.writeByte(key == null ? VERSION_UNSIGNED : VERSION_SIGNED);
            out.writeByte(type);
            out.writeUTF(checkName(player));
            out.writeLong(now);
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            out.write(nonce);
            if (type == STATUS) {
                out.writeBoolean(authenticated);
            } else {
                out.writeUTF(server);
            }
            if (key != null) {
                byte[] content = bytes.toByteArray();
                out.write(key.mac(content, content.length));
            }
            byte[] message = bytes.toByteArray();
            if (message.length > MAX_MESSAGE) {
                throw new IllegalArgumentException("message too large");
            }
            return message;
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String checkName(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("invalid name");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException("invalid name");
            }
        }
        return value;
    }

    private static Result reject(Rejection rejection) {
        return new Result(null, rejection);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        return hmac(key, data, data.length);
    }

    private static byte[] hmac(byte[] key, byte[] data, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(data, 0, length);
            return mac.doFinal();
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
