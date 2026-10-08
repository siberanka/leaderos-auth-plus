package net.leaderos.auth.shared.proxy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides where an authenticated player goes after leaving the auth server.
 *
 * <p>With return-to-requested-server enabled, the server the player originally asked for (see
 * {@link RequestedServers}) wins over the backend's fixed "send after auth" server, which stays the
 * fallback when that connection fails. A backend usually reports the login and then asks for the
 * send-after-auth server a second later. For a short window after a return started - also after the
 * proxy reported the connection as established, since the player is still listed on the auth server
 * until the switch completes - such a request only updates the fallback, so the player is never
 * bounced between two servers.</p>
 */
public final class ReturnRouter {

    /** A return younger than this swallows connect requests (they only update the fallback). */
    static final long IN_FLIGHT_MILLIS = 30_000L;

    private final RequestedServers requested;
    private final Map<UUID, InFlight> inFlight = new ConcurrentHashMap<>();
    private volatile boolean enabled;

    public ReturnRouter(RequestedServers requested, boolean enabled) {
        this.requested = requested;
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public RequestedServers getRequested() {
        return requested;
    }

    /**
     * The backend reported that the player authenticated.
     *
     * @param player       player id
     * @param onAuthServer whether the player is currently on the auth server
     * @param now          current time in milliseconds
     * @return the server to send the player to, or null to leave it where it is
     */
    public String onAuthenticated(UUID player, boolean onAuthServer, long now) {
        if (!enabled || !onAuthServer || isInFlight(player, now)) {
            return null;
        }
        String target = requested.take(player, now);
        if (target == null) {
            return null;
        }
        inFlight.put(player, new InFlight(target, null, now));
        return target;
    }

    /**
     * The backend asked to send an authenticated player to {@code server}.
     *
     * @param player       player id
     * @param server       server named by the backend (send-after-auth)
     * @param onAuthServer whether the player is currently on the auth server
     * @param now          current time in milliseconds
     * @return the server to connect to, or null to ignore the request
     */
    public String onConnectRequest(UUID player, String server, boolean onAuthServer, long now) {
        if (!onAuthServer) {
            return null;
        }
        InFlight current = inFlight.get(player);
        if (current != null && now - current.started <= IN_FLIGHT_MILLIS) {
            current.fallback = server;
            return null;
        }
        if (current != null) {
            inFlight.remove(player, current);
        }
        if (enabled) {
            String target = requested.take(player, now);
            if (target != null) {
                inFlight.put(player, new InFlight(target, server, now));
                return target;
            }
        }
        return server;
    }

    /**
     * The connection started by {@link #onAuthenticated} or {@link #onConnectRequest} finished.
     *
     * @param player  player id
     * @param target  server that was connected to
     * @param success whether the player reached it
     * @return a fallback server to try when it failed, otherwise null
     */
    public String onConnectResult(UUID player, String target, boolean success) {
        InFlight current = inFlight.get(player);
        if (current == null || current.finished || !current.target.equalsIgnoreCase(target)) {
            return null;
        }
        current.finished = true;
        if (success) {
            // Keep the entry until the window ends: a late send-after-auth request must not move the player again.
            return null;
        }
        inFlight.remove(player, current);
        if (current.fallback == null || current.fallback.equalsIgnoreCase(target)) {
            return null;
        }
        return current.fallback;
    }

    public void forget(UUID player) {
        if (player != null) {
            inFlight.remove(player);
            requested.forget(player);
        }
    }

    boolean isInFlight(UUID player, long now) {
        InFlight current = inFlight.get(player);
        return current != null && now - current.started <= IN_FLIGHT_MILLIS;
    }

    private static final class InFlight {
        private final String target;
        private final long started;
        private volatile String fallback;
        private volatile boolean finished;

        private InFlight(String target, String fallback, long started) {
            this.target = target;
            this.fallback = fallback;
            this.started = started;
        }
    }
}
