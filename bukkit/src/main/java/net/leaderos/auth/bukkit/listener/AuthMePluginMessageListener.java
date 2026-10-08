package net.leaderos.auth.bukkit.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.shared.Shared;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Accepts AuthMeBungee/AuthMeVelocity's "perform.login" so their cross-server auto-login keeps working.
 *
 * <p>The message logs a player in without a password, and nothing in it can be verified: it is only
 * trustworthy because proxies drop "BungeeCord" plugin messages sent by clients. It is therefore
 * refused unless the operator enabled it and this server runs behind a proxy; on a server players
 * reach directly, any client could send it.</p>
 */
public class AuthMePluginMessageListener implements PluginMessageListener {

    private final Bukkit plugin;
    private final AtomicBoolean warned = new AtomicBoolean();

    public AuthMePluginMessageListener(Bukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!"BungeeCord".equals(channel) || data == null || data.length > 1024) {
            return;
        }

        String playerName;
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            if (!"AuthMe.v2".equals(in.readUTF()) || !"perform.login".equals(in.readUTF())) {
                return;
            }
            playerName = in.readUTF();
        } catch (RuntimeException malformed) {
            return;
        }

        if (!plugin.getConfigFile().getSettings().getAuthmeBridge().isAcceptProxyLogin()) {
            warnOnce("Refused an AuthMe 'perform.login' message for " + playerName
                    + " (authme-bridge.accept-proxy-login is false).");
            return;
        }
        if (!plugin.isBehindProxy()) {
            warnOnce("Refused an AuthMe 'perform.login' message for " + playerName
                    + ": this server is not behind a proxy, so any client could have sent it.");
            return;
        }

        Player target = plugin.getServer().getPlayerExact(playerName);
        if (target == null || !target.isOnline() || plugin.isAuthenticated(target)) {
            return;
        }

        Shared.getDebugAPI().send("Logging " + target.getName() + " in through an AuthMe proxy message.", false);
        plugin.forceAuthenticate(target);
    }

    private void warnOnce(String message) {
        if (warned.compareAndSet(false, true)) {
            plugin.getLogger().warning(message + " Further refusals are logged in debug mode only.");
        } else {
            Shared.getDebugAPI().send(message, false);
        }
    }
}
