package net.leaderos.auth.bukkit.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import lombok.RequiredArgsConstructor;
import net.leaderos.auth.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

@RequiredArgsConstructor
public class AuthMePluginMessageListener implements PluginMessageListener {

    private final Bukkit plugin;

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!"BungeeCord".equals(channel)) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(data);
        String subChannel = in.readUTF();
        if (!"AuthMe.v2".equals(subChannel)) {
            return;
        }

        String typeId = in.readUTF();
        if (!"perform.login".equals(typeId)) {
            return;
        }

        String playerName = in.readUTF();
        Player target = plugin.getServer().getPlayerExact(playerName);
        if (target == null || !target.isOnline() || plugin.isAuthenticated(target)) {
            return;
        }

        plugin.forceAuthenticate(target);
    }
}
