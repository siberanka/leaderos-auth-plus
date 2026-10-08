package net.leaderos.auth.shared.messaging;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers message nonces long enough to refuse a replay inside the accepted clock window.
 * Bounded: when full, the oldest nonce is forgotten first.
 */
public final class ReplayGuard {

    private final long retentionMillis;
    private final int maxEntries;
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();

    public ReplayGuard() {
        this(AuthChannel.MAX_AGE_MILLIS * 2 + 10_000L, 100_000);
    }

    public ReplayGuard(long retentionMillis, int maxEntries) {
        if (retentionMillis <= 0 || maxEntries <= 0) {
            throw new IllegalArgumentException("retention and capacity must be positive");
        }
        this.retentionMillis = retentionMillis;
        this.maxEntries = maxEntries;
    }

    /**
     * Records a nonce.
     *
     * @param nonce message nonce
     * @param now   current time in milliseconds
     * @return true the first time a nonce is seen inside the retention window
     */
    public synchronized boolean firstUse(byte[] nonce, long now) {
        if (nonce == null || nonce.length == 0) {
            return false;
        }
        purge(now);
        String key = hex(nonce);
        if (seen.containsKey(key)) {
            return false;
        }
        if (seen.size() >= maxEntries) {
            Iterator<String> eldest = seen.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
        seen.put(key, now + retentionMillis);
        return true;
    }

    public synchronized int size() {
        return seen.size();
    }

    private void purge(long now) {
        Iterator<Map.Entry<String, Long>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            // Insertion order equals expiry order, so stop at the first live entry.
            if (iterator.next().getValue() > now) {
                return;
            }
            iterator.remove();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            out.append(Character.forDigit((item >> 4) & 0xf, 16)).append(Character.forDigit(item & 0xf, 16));
        }
        return out.toString();
    }
}
