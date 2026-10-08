package net.leaderos.auth.bukkit.listener;

import lombok.RequiredArgsConstructor;
import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.bukkit.helpers.BossBarUtil;
import net.leaderos.auth.bukkit.helpers.ChatUtil;
import net.leaderos.auth.bukkit.helpers.TitleUtil;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.enums.ErrorCode;
import net.leaderos.auth.shared.enums.SessionState;
import net.leaderos.auth.shared.helpers.AuthUtil;
import net.leaderos.auth.shared.helpers.Placeholder;
import net.leaderos.auth.shared.helpers.UserAgentUtil;
import net.leaderos.auth.shared.model.response.GameSessionResponse;
import net.leaderos.auth.shared.security.DuplicateLoginPolicy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class ConnectionListener implements Listener {

    private final Bukkit plugin;
    /** Online players a quick reconnect of the same profile and address may replace, with an expiry. */
    private final Map<UUID, Long> allowedReplacements = new ConcurrentHashMap<>();
    /** When each pending session was fetched; logins refused later (ban, whitelist, full) never join. */
    private final Map<UUID, Long> pendingSince = new ConcurrentHashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onAsyncLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;

        String playerName = event.getName();
        UUID uniqueId = event.getUniqueId();
        plugin.getPendingSessions().remove(uniqueId);
        purgeStale(System.currentTimeMillis());

        Player online = plugin.getServer().getPlayerExact(playerName);
        if (online != null) {
            String onlineIp = online.getAddress() != null ? online.getAddress().getAddress().getHostAddress() : null;
            if (!DuplicateLoginPolicy.mayReplace(online.getUniqueId(), onlineIp, uniqueId,
                    event.getAddress().getHostAddress())) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "You are already connected to this server!");
                return;
            }
            // Same profile from the same address: a reconnect whose old connection has not closed yet.
            // The server drops the old connection; the new one authenticates on its own.
            allowedReplacements.put(online.getUniqueId(), System.currentTimeMillis() + 30_000L);
            Shared.getDebugAPI().send("Allowing " + playerName + " to replace its previous connection "
                    + "(same profile and address).", false);
        }

        try {
            String ip = event.getAddress().getHostAddress();

            // Make API request to get user game session
            Shared.getDebugAPI().send("Making API request for player " + playerName, false);
            String userAgent = UserAgentUtil.generateUserAgent(!plugin.getConfigFile().getSettings().isSession());

            GameSessionResponse session = AuthUtil.checkGameSession(playerName, ip, userAgent).join();
            plugin.getPendingSessions().put(uniqueId, session);
            pendingSince.put(uniqueId, System.currentTimeMillis());

            // If the session response status is false, handle errors
            if (!session.isStatus()) {
                // Kick the player if they have an invalid username
                if (session.getError() == ErrorCode.INVALID_USERNAME) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, String.join("\n",
                            ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickInvalidUsername(),
                                    new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));

                    plugin.getPendingSessions().remove(uniqueId);
                    return;
                }

                // Kick the player with a generic error message for other errors
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, String.join("\n",
                        ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickAnError(),
                                new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));

                plugin.getPendingSessions().remove(uniqueId);
                return;
            }

            // Kick the player if their username case does not match
            if (session.getUsername() != null && !session.getUsername().equals(playerName)) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, String.join("\n",
                        ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickUsernameCaseMismatch(),
                                new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()),
                                new Placeholder("{valid}", session.getUsername()),
                                new Placeholder("{invalid}", playerName)
                        )));

                plugin.getPendingSessions().remove(uniqueId);
                return;
            }

            // Check email verification status
            if (session.getState() == SessionState.EMAIL_NOT_VERIFIED) {
                // Kick the player if their email is not verified and kicking is enabled
                if (plugin.getConfigFile().getSettings().getEmailVerification().isKickNonVerified()) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, String.join("\n",
                            ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickEmailNotVerified(),
                                    new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));

                    plugin.getPendingSessions().remove(uniqueId);
                    return;
                } else {
                    // If email verification is disabled, set status to LOGIN_REQUIRED
                    session.setState(SessionState.LOGIN_REQUIRED);
                }
            }

            // Kick the player if they are not registered and kicking is enabled
            if (plugin.getConfigFile().getSettings().isKickNonRegistered() && session.getState() == SessionState.REGISTER_REQUIRED) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, String.join("\n",
                        ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickNotRegistered(),
                                new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));

                plugin.getPendingSessions().remove(uniqueId);
                return;
            }

            // If the player is already authenticated, allow them to join directly
            if (session.getState() == SessionState.HAS_SESSION && plugin.getConfigFile().getSettings().isSession()) {
                session.setState(SessionState.AUTHENTICATED);
                session.setToken(session.getToken());
                Shared.getDebugAPI().send("Player " + playerName + " has active session, allowing direct login.", false);
                ChatUtil.sendConsoleInfo(playerName + " has active session, allowing direct login.");
            } else if (session.getState() == SessionState.LOGIN_REQUIRED) {
                plugin.getBedrockTrust().prefetch(uniqueId, playerName);
            }
        } catch (Exception e) {
            Shared.getDebugAPI().send("ErrorCode processing player " + playerName + ": " + e.getMessage(), true);

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, String.join("\n",
                    ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickAnError(),
                            new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));

            plugin.getPendingSessions().remove(uniqueId);
        }
    }

    /**
     * Another plugin (IP limit, whitelist, ban) refused the login after the session was fetched.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncLoginResult(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            plugin.getPendingSessions().remove(event.getUniqueId());
            plugin.getBedrockTrust().forget(event.getUniqueId());
        }
    }

    private void purgeStale(long now) {
        pendingSince.entrySet().removeIf(entry -> {
            if (now - entry.getValue() <= 60_000L) {
                return false;
            }
            plugin.getPendingSessions().remove(entry.getKey());
            return true;
        });
        allowedReplacements.values().removeIf(until -> until < now);
    }

    /**
     * Moves the session fetched at pre-login into place before any other join handling runs.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinEarly(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        allowedReplacements.remove(player.getUniqueId());
        pendingSince.remove(player.getUniqueId());
        GameSessionResponse session = plugin.getPendingSessions().remove(player.getUniqueId());
        if (session != null) {
            plugin.getSessions().put(player.getName(), session);
        } else {
            // Never let a session of an earlier connection stand in for this one.
            plugin.getSessions().remove(player.getName());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getAuthMeCompatBridge().broadcastUnauthenticated(player);
        plugin.getSessions().remove(player.getName());
        plugin.getBedrockTrust().forget(player.getUniqueId());

        // Cleanup Bedrock form tracking
        net.leaderos.auth.bukkit.helpers.BedrockSupport.cleanup(player);

        // Clear title
        if (plugin.getConfigFile().getSettings().isShowTitle()) {
            TitleUtil.clearTitle(player);
        }

        // Clear boss bar
        if (plugin.getConfigFile().getSettings().getBossBar().isEnabled()) {
            BossBarUtil.hideBossBar(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        // Especially for offline CraftBukkit, we need to catch players being kicked because of
        // "logged in from another location" and to cancel their kick - unless it is the same
        // profile reconnecting from the same address, which onAsyncLogin allowed explicitly.
        String reason = event.getReason() == null ? "" : event.getReason().toLowerCase(Locale.ROOT);
        if (!reason.contains("logged in from another location") && !reason.contains("duplicate_login")) {
            return;
        }
        Long until = allowedReplacements.get(event.getPlayer().getUniqueId());
        if (until != null && until >= System.currentTimeMillis()) {
            return;
        }
        event.setCancelled(true);
    }

}
