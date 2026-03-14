package net.leaderos.auth.velocity.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import lombok.RequiredArgsConstructor;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.leaderos.auth.shared.enums.ErrorCode;
import net.leaderos.auth.velocity.Velocity;
import net.leaderos.auth.velocity.handler.AuthSessionHandler;
import net.leaderos.auth.velocity.handler.ValidSessionHandler;
import net.leaderos.auth.velocity.helpers.ChatUtil;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.enums.SessionState;
import net.leaderos.auth.shared.helpers.AuthUtil;
import net.leaderos.auth.shared.helpers.Placeholder;
import net.leaderos.auth.shared.helpers.UserAgentUtil;
import net.leaderos.auth.shared.model.response.GameSessionResponse;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ConnectionListener {
    private final Velocity plugin;

    @Subscribe(order = PostOrder.LAST)
    public void onJoin(LoginLimboRegisterEvent event) {
        // Get the player who is joining
        Player player = event.getPlayer();
        String playerName = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // Add the authentication logic inside event.addOnJoinCallback to ensure that,
        // if there is a system like LimboFilter,
        // it processes the player first before our logic runs.
        event.addOnJoinCallback(() -> {
            try {
                // Make API request for game session
                Shared.getDebugAPI().send("Making API request for player " + playerName, false);
                String userAgent = UserAgentUtil.generateUserAgent(!plugin.getConfigFile().getSettings().isSession());
                GameSessionResponse session = AuthUtil.checkGameSession(playerName, ip, userAgent).join();

                // If the session response status is false, handle errors
                if (!session.isStatus()) {
                    // Kick the player if they have an invalid username
                    if (session.getError() == ErrorCode.INVALID_USERNAME) {
                        kickPlayer(player, plugin.getLangFile().getMessages().getKickInvalidUsername());
                        return;
                    }

                    // Kick the player with a generic error message for other errors
                    kickPlayer(player, plugin.getLangFile().getMessages().getKickAnError());
                    return;
                }

                // Kick the player if their username case does not match
                if (session.getUsername() != null && !session.getUsername().equals(playerName)) {
                    List<String> kickMessage = plugin.getLangFile().getMessages().getKickUsernameCaseMismatch()
                            .stream()
                            .map(s -> s.replace("{valid}", session.getUsername()))
                            .map(s -> s.replace("{invalid}", playerName))
                            .collect(Collectors.toList());
                    kickPlayer(player, kickMessage);
                    return;
                }

                // Check email verification status
                if (session.getState() == SessionState.EMAIL_NOT_VERIFIED) {
                    // Kick the player if their email is not verified and kicking is enabled
                    if (plugin.getConfigFile().getSettings().getEmailVerification().isKickNonVerified()) {
                        kickPlayer(player, plugin.getLangFile().getMessages().getKickEmailNotVerified());
                        return;
                    } else {
                        // If email verification is disabled, set status to LOGIN_REQUIRED
                        session.setState(SessionState.LOGIN_REQUIRED);
                    }
                }

                // Kick the player if they are not registered and kicking is enabled
                if (plugin.getConfigFile().getSettings().isKickNonRegistered() && session.getState() == SessionState.REGISTER_REQUIRED) {
                    kickPlayer(player, plugin.getLangFile().getMessages().getKickNotRegistered());
                    return;
                }

                // If the player is already authenticated, allow them to join directly
                if (session.getState() == SessionState.HAS_SESSION && plugin.getConfigFile().getSettings().isSession()) {
                    // Change session state to authenticated
                    session.setState(SessionState.AUTHENTICATED);

                    Shared.getDebugAPI().send("Player " + playerName + " has active session, allowing direct login.", false);
                    ChatUtil.sendConsoleInfo(playerName + " has logged in with an active session.");
                    ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getLogin().getSuccess());
                    plugin.getLimboServer().spawnPlayer(player, new ValidSessionHandler());
                    return;
                }

                // Spawn player in auth limbo
                Shared.getDebugAPI().send("Spawning player " + playerName + " in limbo for authentication.", false);
                plugin.getLimboServer().spawnPlayer(player, new AuthSessionHandler(plugin, player, ip, session));
            } catch (Exception e) {
                Shared.getDebugAPI().send("ErrorCode processing player " + playerName + ": " + e.getMessage(), true);

                // Kick the player with an error message
                kickPlayer(player, plugin.getLangFile().getMessages().getKickAnError());
            }
        });
    }

    @Subscribe
    public void onServerConnect(com.velocitypowered.api.event.player.ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        
        // If already authenticated, allow any connection
        if (plugin.getAuthenticatedPlayers().getOrDefault(player.getUsername(), false))
            return;

        // Limbo server check (allow connecting to limbo/auth lobby)
        // Note: For Velocity, we check if they are already in Limbo or moving to a server.
        // If they are not authenticated, they should only be allowed to stay in limbo.
        // LimboAPI handles the initial spawn. We just need to prevent /server moves.
        if (event.getResult().getServer().isPresent()) {
            // If they are not authenticated, don't let them move away from wherever they are 
            // unless it's the auth process directing them.
            // However, velocity redirection usually happens via disconnecting from limbo.
            // Let's ensure they can't manually /server.
            event.setResult(com.velocitypowered.api.event.player.ServerPreConnectEvent.ServerResult.denied());
        }
    }

    @Subscribe
    public void onDisconnect(com.velocitypowered.api.event.connection.DisconnectEvent event) {
        plugin.getAuthenticatedPlayers().remove(event.getPlayer().getUsername());
    }

    private void kickPlayer(Player player, List<String> kickMessage) {
        String playerName = player.getUsername();
        Shared.getDebugAPI().send("Player " + playerName + " is being kicked.", false);

        Component message = Component.join(JoinConfiguration.newlines(),
                ChatUtil.replacePlaceholders(kickMessage,
                        new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix())));

        player.disconnect(message);
    }
}