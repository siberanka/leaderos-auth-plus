package net.leaderos.auth.bukkit.helpers;

import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.messaging.AuthChannel;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sends signed login status and connect requests to LeaderOS Auth on the proxy
 * (see {@link AuthChannel}). Nothing is sent when this server is not behind a proxy.
 */
public class ProxyMessenger {

    /** The proxy registers the channel shortly after the player joins; wait at most this many tries. */
    private static final int MAX_ATTEMPTS = 40;
    private static final long RETRY_TICKS = 5L;

    private final Bukkit plugin;
    private final SecureRandom random = new SecureRandom();
    private volatile AuthChannel.Key key;
    private volatile boolean behindProxy;

    public ProxyMessenger(Bukkit plugin) {
        this.plugin = plugin;
    }

    /** Re-reads the proxy mode and the shared secret. */
    public void reload() {
        File root = plugin.getServer().getWorldContainer().getAbsoluteFile();
        boolean bungee = plugin.getServer().spigot().getConfig().getBoolean("settings.bungeecord", false);
        boolean velocity = false;
        List<String> secrets = new ArrayList<>();
        add(secrets, plugin.getConfigFile().getSettings().getProxyMessaging().getSecret());

        File paperGlobal = new File(root, "config/paper-global.yml");
        if (paperGlobal.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(paperGlobal);
            if (yaml.getBoolean("proxies.velocity.enabled")) {
                velocity = true;
                add(secrets, yaml.getString("proxies.velocity.secret"));
            }
        }
        File legacyPaper = new File(root, "paper.yml");
        if (legacyPaper.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyPaper);
            if (yaml.getBoolean("settings.velocity-support.enabled")) {
                velocity = true;
                add(secrets, yaml.getString("settings.velocity-support.secret"));
            }
        }
        if (bungee) {
            File bungeeGuard = new File(plugin.getDataFolder().getParentFile(), "BungeeGuard/config.yml");
            if (bungeeGuard.isFile()) {
                for (String token : YamlConfiguration.loadConfiguration(bungeeGuard).getStringList("allowed-tokens")) {
                    add(secrets, token);
                }
            }
        }

        this.behindProxy = bungee || velocity;
        this.key = secrets.isEmpty() ? null : AuthChannel.Key.derive(secrets.get(0));

        if (bungee && !velocity && plugin.getServer().getPluginManager().getPlugin("BungeeGuard") == null) {
            // Legacy BungeeCord forwarding trusts whatever name, UUID and IP the connection claims.
            plugin.getLogger().severe("This server trusts BungeeCord IP forwarding without BungeeGuard. Anyone who "
                    + "can reach this port directly can join as any player with any IP and skip the auth server. "
                    + "Install BungeeGuard on the proxy and every backend (or use Velocity modern forwarding) and "
                    + "let only the proxy reach the backend ports.");
        }

        if (behindProxy && key == null) {
            plugin.getLogger().severe("No proxy messaging secret is available: login status sent to the proxy "
                    + "is unsigned and LeaderOS Auth on the proxy will refuse it (players stay on the auth server). "
                    + "Set settings.proxy-messaging.secret here and messaging.secret on the proxy to the same "
                    + "value (16+ characters), or use Velocity modern forwarding / BungeeGuard.");
        }
    }

    public boolean isBehindProxy() {
        return behindProxy;
    }

    public boolean hasKey() {
        return key != null;
    }

    /**
     * Reports the login state of a player. The state is read when the message is actually sent, so a
     * report that had to wait for the proxy can never overwrite a newer one with a stale value.
     */
    public void sendStatus(Player player) {
        send(player, () -> AuthChannel.status(key, player.getName(), plugin.isAuthenticated(player),
                System.currentTimeMillis(), random), 0);
    }

    public void sendConnect(Player player, String server) {
        if (server == null || server.trim().isEmpty()) {
            return;
        }
        send(player, () -> AuthChannel.connect(key, player.getName(), server.trim(), System.currentTimeMillis(),
                random), 0);
    }

    private void send(Player player, Supplier<byte[]> message, int attempt) {
        if (!behindProxy || player == null || !player.isOnline()) {
            return;
        }
        if (player.getListeningPluginChannels().contains(AuthChannel.CHANNEL)) {
            // Built now so the timestamp is fresh.
            player.sendPluginMessage(plugin, AuthChannel.CHANNEL, message.get());
            return;
        }
        if (attempt >= MAX_ATTEMPTS) {
            Shared.getDebugAPI().send("The proxy never registered " + AuthChannel.CHANNEL + " for "
                    + player.getName() + "; is LeaderOS Auth installed on the proxy?", true);
            return;
        }
        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> send(player, message, attempt + 1),
                RETRY_TICKS);
    }

    private static void add(List<String> secrets, String secret) {
        if (secret == null) {
            return;
        }
        String value = secret.trim();
        if (AuthChannel.isUsableSecret(value) && !secrets.contains(value)) {
            secrets.add(value);
        }
    }
}
