package net.leaderos.auth.shared.proxy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnRouterTest {

    private static final long TTL = 600_000L;
    private final UUID player = UUID.randomUUID();

    @Test
    void returnsToTheRequestedServerAfterLoginAndKeepsSendAfterAuthAsFallback() {
        ReturnRouter router = new ReturnRouter(new RequestedServers(TTL), true);
        router.getRequested().remember(player, "survival", 0);

        assertEquals("survival", router.onAuthenticated(player, true, 1000));
        // The backend's send-after-auth request a second later must not bounce the player.
        assertNull(router.onConnectRequest(player, "lobby", true, 2000));
        // The return failed (server down): fall back to the backend's server.
        assertEquals("lobby", router.onConnectResult(player, "survival", false));
    }

    @Test
    void successfulReturnHasNoFallback() {
        ReturnRouter router = new ReturnRouter(new RequestedServers(TTL), true);
        router.getRequested().remember(player, "survival", 0);
        assertEquals("survival", router.onAuthenticated(player, true, 0));
        assertNull(router.onConnectRequest(player, "lobby", true, 10));
        assertNull(router.onConnectResult(player, "survival", true));
        // The proxy reports success before the player has left the auth server: a late
        // send-after-auth request must not move it again.
        assertNull(router.onConnectRequest(player, "lobby", true, 20));
        // After the window the router decides normally again.
        assertEquals("lobby", router.onConnectRequest(player, "lobby", true, ReturnRouter.IN_FLIGHT_MILLIS + 1));
        assertEquals("lobby", router.onConnectRequest(player, "lobby", true, ReturnRouter.IN_FLIGHT_MILLIS + 2));
    }

    @Test
    void duplicateResultCallbacksAreIgnored() {
        ReturnRouter router = new ReturnRouter(new RequestedServers(TTL), true);
        router.getRequested().remember(player, "survival", 0);
        assertEquals("survival", router.onAuthenticated(player, true, 0));
        assertNull(router.onConnectRequest(player, "lobby", true, 10));
        assertNull(router.onConnectResult(player, "survival", true));
        assertNull(router.onConnectResult(player, "survival", false), "a late failure after success is ignored");
    }

    @Test
    void connectRequestAloneUsesTheRequestedServerFirst() {
        ReturnRouter router = new ReturnRouter(new RequestedServers(TTL), true);
        router.getRequested().remember(player, "skyblock", 0);

        assertEquals("skyblock", router.onConnectRequest(player, "lobby", true, 5));
        assertEquals("lobby", router.onConnectResult(player, "skyblock", false));
    }

    @Test
    void withoutARequestTheBackendDecides() {
        ReturnRouter router = new ReturnRouter(new RequestedServers(TTL), true);
        assertNull(router.onAuthenticated(player, true, 0));
        assertEquals("lobby", router.onConnectRequest(player, "lobby", true, 0));
    }

    @Test
    void disabledRouterKeepsTheOldBehaviour() {
        ReturnRouter router = new ReturnRouter(new RequestedServers(TTL), false);
        router.getRequested().remember(player, "survival", 0);
        assertNull(router.onAuthenticated(player, true, 0));
        assertEquals("lobby", router.onConnectRequest(player, "lobby", true, 0));
    }

    @Test
    void neverMovesPlayersThatAreNotOnTheAuthServer() {
        ReturnRouter router = new ReturnRouter(new RequestedServers(TTL), true);
        router.getRequested().remember(player, "survival", 0);
        assertNull(router.onAuthenticated(player, false, 0));
        assertNull(router.onConnectRequest(player, "lobby", false, 0));
        // The request is still there for the real login.
        assertEquals("survival", router.onAuthenticated(player, true, 1));
    }

    @Test
    void expiredRequestsAreIgnored() {
        ReturnRouter router = new ReturnRouter(new RequestedServers(TTL), true);
        router.getRequested().remember(player, "survival", 0);
        assertNull(router.onAuthenticated(player, true, TTL + 1));
    }

    @Test
    void staleInFlightReturnStopsSwallowingRequests() {
        ReturnRouter router = new ReturnRouter(new RequestedServers(TTL), true);
        router.getRequested().remember(player, "survival", 0);
        assertEquals("survival", router.onAuthenticated(player, true, 0));
        assertEquals("lobby", router.onConnectRequest(player, "lobby", true, ReturnRouter.IN_FLIGHT_MILLIS + 1));
    }

    @Test
    void forgetClearsEverything() {
        ReturnRouter router = new ReturnRouter(new RequestedServers(TTL), true);
        router.getRequested().remember(player, "survival", 0);
        router.forget(player);
        assertNull(router.onAuthenticated(player, true, 0));
        assertFalse(router.isInFlight(player, 0));
    }

    @Test
    void requestedServersValidateAndExpire() {
        RequestedServers servers = new RequestedServers(1000);
        assertFalse(servers.remember(player, "", 0));
        assertFalse(servers.remember(null, "lobby", 0));
        assertTrue(servers.remember(player, "lobby", 0));
        assertTrue(servers.remember(player, "survival", 1));
        assertEquals("survival", servers.peek(player, 2));
        assertNull(servers.peek(player, 1001));
        servers.sweep(5000);
        assertEquals(0, servers.size());
        assertTrue(RequestedServers.isAuthServer("Auth_Lobby", "auth_lobby"));
        assertFalse(RequestedServers.isAuthServer("lobby", "auth_lobby"));
    }
}
