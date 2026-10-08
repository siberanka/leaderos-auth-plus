package net.leaderos.auth.bukkit.listener;

import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.bukkit.helpers.ChatUtil;
import net.leaderos.auth.shared.helpers.Placeholder;
import net.leaderos.auth.shared.security.IpAddressNormalizer;
import net.leaderos.auth.shared.security.IpConnectionTracker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Limits concurrent connections per IP address on servers players reach directly. Online players
 * are counted live; only logins in progress are tracked (see {@link IpConnectionTracker}).
 */
public class IpConnectionLimitListener implements Listener {

    private final Bukkit plugin;
    private final IpConnectionTracker tracker = new IpConnectionTracker(30_000L);

    public IpConnectionLimitListener(Bukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(AsyncPlayerPreLoginEvent event) {
        int maxPerIP = plugin.getConfigFile().getSettings().getMaxJoinPerIP();
        // Ignore if the limit is disabled, the login is already refused, or the proxy enforces it.
        if (maxPerIP <= 0 || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED
                || plugin.isBehindProxy()) {
            return;
        }

        String ip = event.getAddress().getHostAddress();
        List<String> online = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getAddress() != null
                    && IpAddressNormalizer.sameAddress(player.getAddress().getAddress().getHostAddress(), ip)) {
                online.add(player.getName());
            }
        }

        if (!tracker.tryAdmit(event.getName(), ip, maxPerIP, online, System.currentTimeMillis())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, String.join("\n",
                    ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickMaxConnectionsPerIP(),
                            new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginResult(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            tracker.release(event.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoined(PlayerJoinEvent event) {
        tracker.joined(event.getPlayer().getName());
    }

    @EventHandler
    public void onDisconnect(PlayerQuitEvent event) {
        tracker.release(event.getPlayer().getName());
    }
}
