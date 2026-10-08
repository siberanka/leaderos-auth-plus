package net.leaderos.auth.bungee.listener;

import net.leaderos.auth.bungee.Bungee;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.messaging.AuthChannel;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Signed messages from LeaderOS Auth on the backends (see {@link AuthChannel}). The channel is
 * consumed in both directions: clients never see it and cannot send it to a backend.
 */
public class AuthMessageListener implements Listener {

    private final Bungee plugin;

    public AuthMessageListener(Bungee plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPluginMessage(PluginMessageEvent event) {
        if (!AuthChannel.CHANNEL.equals(event.getTag())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getSender() instanceof Server) || !(event.getReceiver() instanceof ProxiedPlayer)) {
            return;
        }
        Server server = (Server) event.getSender();
        ProxiedPlayer player = (ProxiedPlayer) event.getReceiver();

        AuthChannel.Result result = AuthChannel.read(event.getData(), plugin.getMessagingKeys(),
                !plugin.getConfigFile().getSettings().getMessaging().isRequireSignature(), plugin.getReplayGuard(),
                System.currentTimeMillis());
        if (!result.isAccepted()) {
            Shared.getDebugAPI().send("Refused a login message from " + server.getInfo().getName() + " for "
                    + player.getName() + ": " + result.getRejection(), true);
            return;
        }
        AuthChannel.Message message = result.getMessage();
        // The message must be about the player whose connection carried it.
        if (!player.getName().equalsIgnoreCase(message.getPlayer())) {
            Shared.getDebugAPI().send("SECURITY ALERT: " + server.getInfo().getName() + " sent a login message for "
                    + message.getPlayer() + " through " + player.getName(), true);
            return;
        }

        if (message.getType() == AuthChannel.STATUS) {
            handleStatus(player, message.isAuthenticated());
        } else if (message.getType() == AuthChannel.CONNECT) {
            handleConnect(player, message.getServer());
        }
    }

    void handleStatus(ProxiedPlayer player, boolean authenticated) {
        Shared.getDebugAPI().send("Received auth status for player " + player.getName() + ": " + authenticated, false);
        plugin.setAuthenticated(player, authenticated);
        if (!authenticated) {
            return;
        }
        String target = plugin.getReturnRouter().onAuthenticated(player.getUniqueId(), plugin.isOnAuthServer(player),
                System.currentTimeMillis());
        if (target != null) {
            connect(player, target);
        }
    }

    void handleConnect(ProxiedPlayer player, String server) {
        if (!plugin.isOnAuthServer(player)) {
            // Redirection is only allowed while the player is on the auth server.
            Shared.getDebugAPI().send("REJECTED: Redirection request for " + player.getName()
                    + " while not on the auth server", true);
            return;
        }
        Shared.getDebugAPI().send("Received redirection request for player " + player.getName() + " to " + server, false);
        // Mark as authenticated first to allow the move
        plugin.setAuthenticated(player, true);
        String target = plugin.getReturnRouter().onConnectRequest(player.getUniqueId(), server, true,
                System.currentTimeMillis());
        if (target != null) {
            connect(player, target);
        }
    }

    /**
     * Sends the player on through a new connection request, so every plugin checks the destination.
     */
    private void connect(ProxiedPlayer player, String target) {
        UUID uniqueId = player.getUniqueId();
        ServerInfo info = plugin.getProxy().getServerInfo(target);
        if (info == null || !info.canAccess(player)) {
            Shared.getDebugAPI().send(player.getName() + " cannot be sent to " + target + "; it is missing or "
                    + "restricted.", false);
            String fallback = plugin.getReturnRouter().onConnectResult(uniqueId, target, false);
            if (fallback != null && !fallback.equalsIgnoreCase(target)) {
                connect(player, fallback);
            }
            return;
        }
        // Run after the current plugin message has been handled.
        plugin.getProxy().getScheduler().schedule(plugin, () -> {
            if (!player.isConnected()) {
                return;
            }
            player.connect(info, (success, error) -> {
                String fallback = plugin.getReturnRouter().onConnectResult(uniqueId, target,
                        Boolean.TRUE.equals(success));
                if (fallback != null && player.isConnected()) {
                    Shared.getDebugAPI().send(player.getName() + " could not reach " + target + "; sending it to "
                            + fallback + " instead.", false);
                    connect(player, fallback);
                }
            }, ServerConnectEvent.Reason.PLUGIN);
        }, 50, TimeUnit.MILLISECONDS);
    }
}
