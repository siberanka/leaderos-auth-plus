package net.leaderos.auth.shared.proxy;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server each unauthenticated player originally asked for (proxy default server, forced host,
 * a reconnect routed by another plugin such as twilight-proxy), kept for a short time while the
 * player is held on the auth server. After a successful login the player is sent there through a
 * new connection request, so every plugin's checks run again. Pure bookkeeping with an explicit
 * clock, so it is tested without a proxy.
 */
public final class RequestedServers {

    private static final int MAX_TRACKED = 100_000;

    private final long ttlMillis;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public RequestedServers(long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.ttlMillis = ttlMillis;
    }

    /**
     * Remembers the server a player asked for; a later request replaces it.
     *
     * @param player player id
     * @param server requested server name
     * @param now    current time in milliseconds
     * @return false when it was not stored (invalid input or full)
     */
    public boolean remember(UUID player, String server, long now) {
        if (player == null || server == null || server.trim().isEmpty() || server.length() > 64) {
            return false;
        }
        if (entries.size() >= MAX_TRACKED && !entries.containsKey(player)) {
            sweep(now);
            if (entries.size() >= MAX_TRACKED) {
                return false;
            }
        }
        entries.put(player, new Entry(server, now + ttlMillis));
        return true;
    }

    /**
     * Removes and returns the live request of a player.
     *
     * @param player player id
     * @param now    current time in milliseconds
     * @return requested server, or null when there is none or it expired
     */
    public String take(UUID player, long now) {
        if (player == null) {
            return null;
        }
        Entry entry = entries.remove(player);
        return entry == null || entry.expires <= now ? null : entry.server;
    }

    /**
     * Returns the live request of a player without removing it.
     */
    public String peek(UUID player, long now) {
        if (player == null) {
            return null;
        }
        Entry entry = entries.get(player);
        return entry == null || entry.expires <= now ? null : entry.server;
    }

    public void forget(UUID player) {
        if (player != null) {
            entries.remove(player);
        }
    }

    /** Removes expired entries. */
    public void sweep(long now) {
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expires <= now) {
                iterator.remove();
            }
        }
    }

    public int size() {
        return entries.size();
    }

    /** True when {@code server} names the auth server (case-insensitive). */
    public static boolean isAuthServer(String server, String authServer) {
        return server != null && authServer != null
                && server.toLowerCase(Locale.ROOT).equals(authServer.toLowerCase(Locale.ROOT));
    }

    private static final class Entry {
        private final String server;
        private final long expires;

        private Entry(String server, long expires) {
            this.server = server;
            this.expires = expires;
        }
    }
}
