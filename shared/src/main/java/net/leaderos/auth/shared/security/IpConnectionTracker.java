package net.leaderos.auth.shared.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-IP connection limit without counters that can drift.
 *
 * <p>Players who are online are counted live from the server's player list by the caller; this
 * class only tracks logins that were admitted but are not in that list yet (pending). A pending
 * login ends when the player appears online, when the login fails, or after a timeout, so a
 * cancelled or crashed login can never leak a slot, a server switch can never count twice, and
 * simultaneous logins from one address are serialized. A login that replaces the same name from
 * the same address (a reconnect while the old connection is still closing) does not take an extra
 * slot.</p>
 */
public final class IpConnectionTracker {

    private static final int MAX_PENDING = 100_000;

    private final LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
    private volatile long pendingTimeoutMillis;

    public IpConnectionTracker(long pendingTimeoutMillis) {
        setPendingTimeoutMillis(pendingTimeoutMillis);
    }

    public void setPendingTimeoutMillis(long pendingTimeoutMillis) {
        this.pendingTimeoutMillis = Math.max(5_000L, pendingTimeoutMillis);
    }

    /**
     * Admits a login if the address is below its limit.
     *
     * @param name             joining player name
     * @param ip               joining address
     * @param max              connections allowed per address
     * @param onlineNamesFromIp names of players online from the same address right now
     * @param now              current time in milliseconds
     * @return true when the login is admitted (and now pending)
     */
    public synchronized boolean tryAdmit(String name, String ip, int max, Collection<String> onlineNamesFromIp,
            long now) {
        if (max <= 0) {
            return true;
        }
        String key = key(name);
        String address = address(ip);
        purge(now);

        Set<String> online = new HashSet<>();
        for (String onlineName : onlineNamesFromIp) {
            online.add(key(onlineName));
        }
        // A reconnect of the same name replaces the old connection instead of adding one.
        online.remove(key);

        int used = online.size();
        for (Map.Entry<String, Pending> entry : pending.entrySet()) {
            if (!entry.getKey().equals(key) && entry.getValue().ip.equals(address)
                    && !online.contains(entry.getKey())) {
                used++;
            }
        }
        if (used >= max) {
            return false;
        }
        pending.remove(key);
        if (pending.size() >= MAX_PENDING) {
            Iterator<String> eldest = pending.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
        pending.put(key, new Pending(address, now + pendingTimeoutMillis));
        return true;
    }

    /** The player is now in the online list; it is counted from there. */
    public synchronized void joined(String name) {
        pending.remove(key(name));
    }

    /** The login failed or the player left before it appeared online. */
    public synchronized void release(String name) {
        pending.remove(key(name));
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    private void purge(long now) {
        Iterator<Pending> iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expires <= now) {
                iterator.remove();
            }
        }
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static String address(String ip) {
        if (ip == null) {
            return "";
        }
        try {
            return IpAddressNormalizer.exact(ip);
        } catch (IllegalArgumentException invalid) {
            return ip.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static final class Pending {
        private final String ip;
        private final long expires;

        private Pending(String ip, long expires) {
            this.ip = ip;
            this.expires = expires;
        }
    }
}
