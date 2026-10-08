package net.leaderos.auth.velocity.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import lombok.RequiredArgsConstructor;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.messaging.AuthChannel;
import net.leaderos.auth.velocity.Velocity;

/**
 * Signed messages from LeaderOS Auth on backend servers (see {@link AuthChannel}). The channel is
 * consumed in both directions: clients never see it and cannot send it to a backend.
 */
@RequiredArgsConstructor
public class PluginMessageListener {

    private final Velocity plugin;

    @Subscribe(order = PostOrder.FIRST)
    public void onPluginMessage(PluginMessageEvent event) {
        if (!AuthChannel.CHANNEL.equals(event.getIdentifier().getId())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection)) return;

        ServerConnection connection = (ServerConnection) event.getSource();
        Player messageReceiver = connection.getPlayer();
        AuthChannel.Result result = AuthChannel.read(event.getData(), plugin.getMessagingKeys(), false,
                plugin.getReplayGuard(), System.currentTimeMillis());
        if (!result.isAccepted()) {
            Shared.getDebugAPI().send("Refused a login message from " + connection.getServerInfo().getName()
                    + " for " + messageReceiver.getUsername() + ": " + result.getRejection(), true);
            return;
        }
        AuthChannel.Message message = result.getMessage();

        // SECURITY: Verify that the message is for the player through whom it was sent
        if (!messageReceiver.getUsername().equalsIgnoreCase(message.getPlayer())) {
            Shared.getDebugAPI().send("SECURITY ALERT: " + connection.getServerInfo().getName()
                    + " sent a login message for " + message.getPlayer() + " through " + messageReceiver.getUsername(),
                    true);
            return;
        }

        if (message.getType() == AuthChannel.STATUS) {
            Shared.getDebugAPI().send("Received auth status for player " + message.getPlayer() + ": "
                    + message.isAuthenticated(), false);
            plugin.setAuthenticated(messageReceiver, message.isAuthenticated());
        } else if (message.getType() == AuthChannel.CONNECT) {
            Shared.getDebugAPI().send("Received redirection request for player " + message.getPlayer() + " to "
                    + message.getServer(), false);

            // Mark as authenticated first to allow the move
            plugin.setAuthenticated(messageReceiver, true);
            // A new connection request, so every plugin checks the destination as well.
            plugin.getServer().getServer(message.getServer())
                    .ifPresent(server -> messageReceiver.createConnectionRequest(server).fireAndForget());
        }
    }

}
