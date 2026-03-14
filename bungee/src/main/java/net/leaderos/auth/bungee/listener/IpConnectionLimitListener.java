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
    private final Map<String, Long> lastJoinTime = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PreLoginEvent event) {
        // Ignore if max connections per IP is not enabled
        if (plugin.getConfigFile().getSettings().getMaxJoinPerIP() <= 0)
            return;

        String ip = event.getConnection().getAddress().getAddress().getHostAddress();
        int maxPerIP = plugin.getConfigFile().getSettings().getMaxJoinPerIP();

        // Atomically check and verify the connection count
        boolean[] denied = { false };
        ipConnections.compute(ip, (k, current) -> {
            int count = current == null ? 0 : current;
            long now = System.currentTimeMillis();
            long last = lastJoinTime.getOrDefault(ip, 0L);

            // If count is within limit, just increment (O(1) operation)
            if (count < maxPerIP) {
                lastJoinTime.put(ip, now);
                return count + 1;
            }

            // Limit reached? Check if we are in the "strict window" to prevent race condition bypass
            if (now - last > 3000) {
                // Window expired, we can trust a new scan to self-heal
                long actualOnlineCount = plugin.getProxy().getPlayers().stream()
                        .filter(p -> p.getAddress().getAddress().getHostAddress().equals(ip))
                        .count();

                if (actualOnlineCount < maxPerIP) {
                    // It was a leak! Allow the connection.
                    lastJoinTime.put(ip, now);
                    return (int) actualOnlineCount + 1;
                }

                // Still over limit
                denied[0] = true;
                return (int) actualOnlineCount;
            }

            // Inside strict window: Deny immediately to prevent simultaneous join bypass
            denied[0] = true;
            return count;
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
        ipConnections.computeIfPresent(ip, (k, v) -> {
            if (v <= 1) {
                lastJoinTime.remove(ip);
                return null;
            }
            return v - 1;
        });
    }
}
