package net.leaderos.auth.bungee.listener;

import net.leaderos.auth.bungee.Bungee;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.security.IpAddressNormalizer;
import net.leaderos.auth.shared.security.IpConnectionTracker;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Limits concurrent connections per IP address. Online players are counted live from the proxy;
 * only logins in progress are tracked (see {@link IpConnectionTracker}), so server switches never
 * count twice and failed logins never leak a slot.
 */
public class IpConnectionLimitListener implements Listener {

    private final Bungee plugin;
    private final IpConnectionTracker tracker = new IpConnectionTracker(30_000L);

    public IpConnectionLimitListener(Bungee plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PreLoginEvent event) {
        int maxPerIP = plugin.getConfigFile().getSettings().getMaxJoinPerIP();
        if (maxPerIP <= 0 || event.isCancelled() || !(event.getConnection().getSocketAddress() instanceof InetSocketAddress))
            return;

        String ip = ((InetSocketAddress) event.getConnection().getSocketAddress()).getAddress().getHostAddress();
        List<String> online = new ArrayList<>();
        for (ProxiedPlayer player : plugin.getProxy().getPlayers()) {
            if (player.getSocketAddress() instanceof InetSocketAddress && IpAddressNormalizer.sameAddress(
                    ((InetSocketAddress) player.getSocketAddress()).getAddress().getHostAddress(), ip)) {
                online.add(player.getName());
            }
        }

        if (!tracker.tryAdmit(event.getConnection().getName(), ip, maxPerIP, online, System.currentTimeMillis())) {
            Shared.getDebugAPI().send("Refused " + event.getConnection().getName() + " from " + ip
                    + ": connection limit per IP reached.", false);
            event.setCancelReason(new TextComponent(
                    ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfigFile().getSettings().getKickMaxConnectionsPerIP())));
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = (byte) 127)
    public void onPreLoginResult(PreLoginEvent event) {
        if (event.isCancelled()) {
            tracker.release(event.getConnection().getName());
        }
    }

    @EventHandler(priority = (byte) 127)
    public void onLoginResult(LoginEvent event) {
        if (event.isCancelled()) {
            tracker.release(event.getConnection().getName());
        }
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        tracker.joined(event.getPlayer().getName());
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        tracker.release(event.getPlayer().getName());
    }
}
