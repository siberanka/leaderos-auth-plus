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
    private final Map<String, Long> lastJoinTime = new ConcurrentHashMap<>();

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
                long actualOnlineCount = plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> p.getAddress().getAddress().getHostAddress().equals(ip))
                        .count();

                if (actualOnlineCount < maxPerIP) {
                    // It was a leak! Allow the connection.
                    lastJoinTime.put(ip, now);
                    return (int) actualOnlineCount + 1;
                }
                
                // Still over limit
                denied[0] = true;
                return (int) actualOnlineCount; // Sync map with reality
            }

            // Inside strict window: Deny immediately to prevent simultaneous join bypass
            denied[0] = true;
            return count;
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
        ipConnections.computeIfPresent(ip, (k, v) -> {
            if (v <= 1) {
                lastJoinTime.remove(ip);
                return null;
            }
            return v - 1;
        });
    }
}
