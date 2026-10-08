package net.leaderos.auth.bungee.listener;

import net.leaderos.auth.bungee.Bungee;
import net.leaderos.auth.bungee.configuration.Config;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.enums.SessionState;
import net.leaderos.auth.shared.helpers.AuthUtil;
import net.leaderos.auth.shared.helpers.UserAgentUtil;
import net.leaderos.auth.shared.model.response.GameSessionResponse;
import net.leaderos.auth.shared.security.BedrockLinkStore;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Proxy-side session awareness. While a player logs in to the proxy, the LeaderOS panel is asked for
 * the player's game session with the same name, IP and user agent the auth server uses. If the panel
 * reports a valid session - or, with bedrock.trust-xbox, the player is a Floodgate player whose XUID
 * is bound to the account - the player is marked authenticated before its first server is chosen,
 * so it goes where it asked to instead of the auth server. The panel keeps the last word; any error
 * or timeout falls back to the auth server.
 */
public class SessionListener implements Listener {

    /** A decision must be used by the first server connection right after the login. */
    private static final long DECISION_TTL_MILLIS = 60_000L;
    private static final int MAX_DECISIONS = 100_000;

    private final Bungee plugin;
    private final Map<UUID, Decision> decisions = new ConcurrentHashMap<>();

    public SessionListener(Bungee plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(LoginEvent event) {
        PendingConnection connection = event.getConnection();
        if (connection.getUniqueId() != null) {
            decisions.remove(connection.getUniqueId());
        }
        if (event.isCancelled() || !isConfigured() || connection.getUniqueId() == null
                || connection.getAddress() == null || decisions.size() >= MAX_DECISIONS) {
            return;
        }

        String name = connection.getName();
        UUID uniqueId = connection.getUniqueId();
        String ip = connection.getAddress().getAddress().getHostAddress();
        Config.Settings settings = plugin.getConfigFile().getSettings();

        event.registerIntent(plugin);
        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            try {
                GameSessionResponse session = AuthUtil.checkGameSession(name, ip,
                        UserAgentUtil.generateUserAgent(false))
                        .get(settings.getSessionCheckTimeoutMillis(), TimeUnit.MILLISECONDS);
                if (!session.isStatus() || session.getState() == null
                        || (session.getUsername() != null && !session.getUsername().equals(name))) {
                    return;
                }
                if (session.getState() == SessionState.HAS_SESSION && settings.isSession()) {
                    decisions.put(uniqueId, new Decision(name, true, null, System.currentTimeMillis()));
                } else if (session.getState() == SessionState.LOGIN_REQUIRED && plugin.getBedrockDatabase() != null) {
                    BedrockLinkStore.Link link = plugin.getBedrockDatabase().getStore().find(name);
                    if (link != null) {
                        decisions.put(uniqueId, new Decision(name, false, link, System.currentTimeMillis()));
                    }
                }
            } catch (Exception failure) {
                // Timeouts and panel errors leave the decision to the auth server.
                Shared.getDebugAPI().send("Proxy session check for " + name + " failed: " + failure, false);
            } finally {
                event.completeIntent(plugin);
            }
        });
    }

    /**
     * Applies the login decision of a player at its first server connection.
     *
     * @return true when the player was marked authenticated
     */
    public boolean applyFirstConnection(ProxiedPlayer player) {
        Decision decision = decisions.remove(player.getUniqueId());
        if (decision == null || !decision.name.equals(player.getName())
                || System.currentTimeMillis() - decision.created > DECISION_TTL_MILLIS) {
            return false;
        }
        if (decision.sessionValid) {
            plugin.setAuthenticated(player, true);
            Shared.getDebugAPI().send(player.getName() + " has a valid panel session; skipping the auth server.",
                    false);
            return true;
        }
        if (decision.link != null && trustsXbox(player, decision.link)) {
            plugin.setAuthenticated(player, true);
            plugin.getLogger().info(player.getName() + " logged in with the Xbox account bound to it; skipping the "
                    + "auth server.");
            return true;
        }
        return false;
    }

    public void forget(UUID player) {
        if (player != null) {
            decisions.remove(player);
        }
    }

    public void sweep(long now) {
        Iterator<Decision> iterator = decisions.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().created > DECISION_TTL_MILLIS) {
                iterator.remove();
            }
        }
    }

    private boolean isConfigured() {
        Config.Settings settings = plugin.getConfigFile().getSettings();
        String url = settings.getUrl();
        String apiKey = settings.getApiKey();
        return (settings.isSession() || plugin.getBedrockDatabase() != null)
                && url != null && !url.isEmpty() && !"https://yourwebsite.com".equals(url)
                && apiKey != null && !apiKey.isEmpty() && !"YOUR_API_KEY".equals(apiKey);
    }

    private boolean trustsXbox(ProxiedPlayer player, BedrockLinkStore.Link link) {
        if (plugin.getProxy().getPluginManager().getPlugin("floodgate") == null) {
            return false;
        }
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            if (!api.isFloodgatePlayer(player.getUniqueId())) {
                return false;
            }
            FloodgatePlayer floodgatePlayer = api.getPlayer(player.getUniqueId());
            long maxAge = TimeUnit.DAYS.toMillis(plugin.getConfigFile().getSettings().getBedrock()
                    .getTrustMaxAgeDays());
            return floodgatePlayer != null
                    && link.trusts(floodgatePlayer.getXuid(), maxAge, System.currentTimeMillis());
        } catch (Exception | LinkageError unavailable) {
            return false;
        }
    }

    private static final class Decision {
        private final String name;
        private final boolean sessionValid;
        private final BedrockLinkStore.Link link;
        private final long created;

        private Decision(String name, boolean sessionValid, BedrockLinkStore.Link link, long created) {
            this.name = name;
            this.sessionValid = sessionValid;
            this.link = link;
            this.created = created;
        }
    }
}
