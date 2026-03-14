package net.leaderos.auth.velocity.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.ServerConnection;
import lombok.RequiredArgsConstructor;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.velocity.Velocity;

@RequiredArgsConstructor
public class PluginMessageListener {

    private final Velocity plugin;

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equals("BungeeCord")) return;
        if (!(event.getSource() instanceof ServerConnection)) return;

        final ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String channel = in.readUTF();
        if (!channel.equals("Forward")) return;

        in.readUTF();
        String subChannel = in.readUTF();
        if (!subChannel.equals("losauth:status") && !subChannel.equals("losauth:connect")) return;

        final short dataLength = in.readShort();
        final byte[] dataBytes = new byte[dataLength];
        in.readFully(dataBytes);
        final ByteArrayDataInput dataIn = ByteStreams.newDataInput(dataBytes);
        String playerName = dataIn.readUTF();

        // SECURITY: Verify that the message is for the player through whom it was sent
        ServerConnection connection = (ServerConnection) event.getSource();
        Player messageReceiver = connection.getPlayer();
        if (!messageReceiver.getUsername().equalsIgnoreCase(playerName)) {
            Shared.getDebugAPI().send("SECURITY ALERT: Player " + messageReceiver.getUsername() + 
                    " tried to spoof auth message for " + playerName, true);
            return;
        }

        if (subChannel.equals("losauth:status")) {
            boolean isAuthenticated = dataIn.readBoolean();
            Shared.getDebugAPI().send("Received auth status for player " + playerName + ": " + isAuthenticated, false);
            plugin.getAuthenticatedPlayers().put(playerName, isAuthenticated);
        } else if (subChannel.equals("losauth:connect")) {
            String targetServer = dataIn.readUTF();
            
            // SECURITY: Redirection only allowed via auth process (Velocity doesn't have an 'auth server' name
            // in the same way Bungee does since it uses Limbo, but we ensure identity matches)
            Shared.getDebugAPI().send("Received redirection request for player " + playerName + " to " + targetServer, false);

            // Mark as authenticated first to allow the move
            plugin.getAuthenticatedPlayers().put(playerName, true);

            plugin.getServer().getServer(targetServer).ifPresent(messageReceiver::createConnectionRequest);
        }
    }

}
