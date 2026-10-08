package net.leaderos.auth.bungee.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import lombok.RequiredArgsConstructor;
import net.leaderos.auth.bungee.Bungee;
import net.leaderos.auth.shared.Shared;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

/**
 * Unsigned "losauth:status" / "losauth:connect" messages of LeaderOS Auth 1.0.x backends, carried in
 * BungeeCord "Forward" messages. They cannot be verified, so they are only honoured while
 * messaging.require-signature is false (during an upgrade); otherwise they are ignored.
 */
@RequiredArgsConstructor
public class PluginMessageListener implements Listener {

    private final Bungee plugin;

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals("BungeeCord")) return;
        if (!(event.getSender() instanceof Server)) return;
        if (!(event.getReceiver() instanceof ProxiedPlayer)) return;

        String subChannel;
        String playerName;
        ByteArrayDataInput dataIn;
        try {
            final ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
            if (!in.readUTF().equals("Forward")) return;
            in.readUTF();
            subChannel = in.readUTF();
            if (!subChannel.equals("losauth:status") && !subChannel.equals("losauth:connect")) return;

            final short dataLength = in.readShort();
            if (dataLength <= 0) return;
            final byte[] dataBytes = new byte[dataLength];
            in.readFully(dataBytes);
            dataIn = ByteStreams.newDataInput(dataBytes);
            playerName = dataIn.readUTF();
        } catch (RuntimeException malformed) {
            return;
        }

        // Never forward these to the other backends.
        event.setCancelled(true);

        ProxiedPlayer messageReceiver = (ProxiedPlayer) event.getReceiver();
        if (plugin.getConfigFile().getSettings().getMessaging().isRequireSignature()) {
            Shared.getDebugAPI().send("Ignored an unsigned legacy " + subChannel + " message for " + playerName
                    + "; update LeaderOS Auth on " + ((Server) event.getSender()).getInfo().getName() + ".", true);
            return;
        }

        // SECURITY: Verify that the message is coming from the player whose name is in the message
        if (!messageReceiver.getName().equalsIgnoreCase(playerName)) {
            Shared.getDebugAPI().send("SECURITY ALERT: Player " + messageReceiver.getName() +
                    " tried to spoof auth message for " + playerName, true);
            return;
        }

        try {
            if (subChannel.equals("losauth:status")) {
                plugin.getAuthMessageListener().handleStatus(messageReceiver, dataIn.readBoolean());
            } else {
                plugin.getAuthMessageListener().handleConnect(messageReceiver, dataIn.readUTF());
            }
        } catch (RuntimeException malformed) {
            Shared.getDebugAPI().send("Malformed legacy auth message for " + playerName, true);
        }
    }

}
