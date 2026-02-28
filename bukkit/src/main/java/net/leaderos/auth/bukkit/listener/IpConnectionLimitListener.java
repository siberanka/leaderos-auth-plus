package net.leaderos.auth.bukkit.listener;

import lombok.RequiredArgsConstructor;
import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.shared.helpers.Placeholder;
import net.leaderos.auth.bukkit.helpers.ChatUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class IpConnectionLimitListener implements Listener {
    private final Bukkit plugin;
    private final Map<String, Integer> ipConnections = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(AsyncPlayerPreLoginEvent event) {
        // Ignore if max connections per IP is not enabled
        if (plugin.getConfigFile().getSettings().getMaxJoinPerIP() <= 0)
            return;

        // Ignore if Spigot is operating behind a proxy
        boolean isBungee = plugin.getServer().spigot().getConfig().getBoolean("settings.bungeecord", false);
        if (isBungee) {
            return;
        }

        String ip = event.getAddress().getHostAddress();
        int maxPerIP = plugin.getConfigFile().getSettings().getMaxJoinPerIP();

        // Atomically check and increment the connection count
        boolean[] denied = { false };
        ipConnections.compute(ip, (k, current) -> {
            denied[0] = false; // Fix CAS retry state leak
            int count = current == null ? 0 : current;
            if (count >= maxPerIP) {
                denied[0] = true;
                return current; // Don't increment
            }
            return count + 1;
        });

        if (denied[0]) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, String.join("\n",
                    ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickMaxConnectionsPerIP(),
                            new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));
        }
    }

    @EventHandler
    public void onDisconnect(PlayerQuitEvent event) {
        // Ignore if max connections per IP is not enabled
        if (plugin.getConfigFile().getSettings().getMaxJoinPerIP() <= 0)
            return;

        // Decrease the connection count for the IP
        String ip = event.getPlayer().getAddress().getAddress().getHostAddress();
        ipConnections.computeIfPresent(ip, (k, v) -> v > 1 ? v - 1 : null);
    }
}
