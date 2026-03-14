package net.leaderos.auth.velocity.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.leaderos.auth.shared.helpers.Placeholder;
import net.leaderos.auth.velocity.Velocity;
import net.leaderos.auth.velocity.helpers.ChatUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class IpConnectionLimitListener {
    private final Velocity plugin;
    private final Map<String, Integer> ipConnections = new ConcurrentHashMap<>();
    private final Map<String, Long> lastJoinTime = new ConcurrentHashMap<>();

    @Subscribe(order = PostOrder.LAST)
    public void onJoin(PreLoginEvent event) {
        // Ignore if max connections per IP is not enabled
        if (plugin.getConfigFile().getSettings().getMaxJoinPerIP() <= 0)
            return;

        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
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
                long actualOnlineCount = plugin.getServer().getAllPlayers().stream()
                        .filter(p -> p.getRemoteAddress().getAddress().getHostAddress().equals(ip))
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
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.join(JoinConfiguration.newlines(),
                            ChatUtil.replacePlaceholders(
                                    plugin.getLangFile().getMessages().getKickMaxConnectionsPerIP(),
                                    new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix())))));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        // Ignore if max connections per IP is not enabled
        if (plugin.getConfigFile().getSettings().getMaxJoinPerIP() <= 0)
            return;

        // Decrease the connection count for the IP
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        ipConnections.computeIfPresent(ip, (k, v) -> {
            if (v <= 1) {
                lastJoinTime.remove(ip);
                return null;
            }
            return v - 1;
        });
    }
}
