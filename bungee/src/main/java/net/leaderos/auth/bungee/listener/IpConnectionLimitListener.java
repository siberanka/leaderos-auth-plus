package net.leaderos.auth.bungee.listener;

import lombok.RequiredArgsConstructor;
import net.leaderos.auth.bungee.Bungee;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class IpConnectionLimitListener implements Listener {
    private final Bungee plugin;
    private final Map<String, Integer> ipConnections = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PreLoginEvent event) {
        // Ignore if max connections per IP is not enabled
        if (plugin.getConfigFile().getSettings().getMaxJoinPerIP() <= 0)
            return;

        String ip = event.getConnection().getAddress().getAddress().getHostAddress();
        int maxPerIP = plugin.getConfigFile().getSettings().getMaxJoinPerIP();

        // Atomically check and increment the connection count
        boolean[] denied = { false };
        ipConnections.compute(ip, (k, current) -> {
            int count = current == null ? 0 : current;
            if (count >= maxPerIP) {
                denied[0] = true;
                return current; // Don't increment
            }
            return count + 1;
        });

        if (denied[0]) {
            event.setCancelReason(new TextComponent(
                    ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfigFile().getSettings().getKickMaxConnectionsPerIP())));
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        // Ignore if max connections per IP is not enabled
        if (plugin.getConfigFile().getSettings().getMaxJoinPerIP() <= 0)
            return;

        // Decrease the connection count for the IP
        String ip = event.getPlayer().getAddress().getAddress().getHostAddress();
        ipConnections.computeIfPresent(ip, (k, v) -> v > 1 ? v - 1 : null);
    }
}
