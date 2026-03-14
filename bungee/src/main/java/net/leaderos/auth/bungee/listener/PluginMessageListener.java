package net.leaderos.auth.bungee.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import lombok.RequiredArgsConstructor;
import net.leaderos.auth.bungee.Bungee;
import net.leaderos.auth.shared.Shared;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

@RequiredArgsConstructor
public class PluginMessageListener implements Listener {

    private final Bungee plugin;

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals("BungeeCord")) return;
        if (!(event.getSender() instanceof Server)) return;

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

        // SECURITY: Verify that the message is coming from the player whose name is in the message
        // event.getReceiver() is the player involved in the Forward message
        if (!(event.getReceiver() instanceof net.md_5.bungee.api.connection.ProxiedPlayer)) return;
        net.md_5.bungee.api.connection.ProxiedPlayer messageReceiver = (net.md_5.bungee.api.connection.ProxiedPlayer) event.getReceiver();
        
        if (!messageReceiver.getName().equalsIgnoreCase(playerName)) {
            Shared.getDebugAPI().send("SECURITY ALERT: Player " + messageReceiver.getName() + 
                    " tried to spoof auth message for " + playerName, true);
            return;
        }

        if (subChannel.equals("losauth:status")) {
            boolean isAuthenticated = dataIn.readBoolean();
            Shared.getDebugAPI().send("Received auth status for player " + playerName + ": " + isAuthenticated, false);
            plugin.getAuthenticatedPlayers().put(playerName, isAuthenticated);
        } else if (subChannel.equals("losauth:connect")) {
            String targetServer = dataIn.readUTF();
            
            // SECURITY: Only allow redirection if the player is currently on the auth server
            String currentServer = messageReceiver.getServer().getInfo().getName();
            String authServer = plugin.getConfigFile().getSettings().getAuthServer();
            if (!currentServer.equals(authServer)) {
                Shared.getDebugAPI().send("REJECTED: Redirection request for " + playerName + 
                        " while not on auth server (Current: " + currentServer + ")", true);
                return;
            }

            Shared.getDebugAPI().send("Received redirection request for player " + playerName + " to " + targetServer, false);

            // Mark as authenticated first to allow the move
            plugin.getAuthenticatedPlayers().put(playerName, true);

            net.md_5.bungee.api.config.ServerInfo serverInfo = plugin.getProxy().getServerInfo(targetServer);
            if (serverInfo != null) {
                messageReceiver.connect(serverInfo);
            }
        }
    }

}
