package net.leaderos.auth.velocity.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.leaderos.auth.shared.helpers.Placeholder;
import net.leaderos.auth.shared.security.IpAddressNormalizer;
import net.leaderos.auth.shared.security.IpConnectionTracker;
import net.leaderos.auth.velocity.Velocity;
import net.leaderos.auth.velocity.helpers.ChatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Limits concurrent connections per IP address. Online players are counted live from the proxy;
 * logins in progress - including players still in the auth limbo, which Velocity does not list
 * yet - are tracked (see {@link IpConnectionTracker}), so failed logins never leak a slot and
 * server switches never count twice.
 */
public class IpConnectionLimitListener {

    private final Velocity plugin;
    private final IpConnectionTracker tracker;

    public IpConnectionLimitListener(Velocity plugin) {
        this.plugin = plugin;
        this.tracker = new IpConnectionTracker(pendingTimeout());
    }

    private long pendingTimeout() {
        // A player may stay in the auth limbo for the whole auth timeout before Velocity lists it.
        return (Math.max(10, plugin.getConfigFile().getSettings().getAuthTimeout()) + 60) * 1000L;
    }

    @Subscribe(order = PostOrder.LAST)
    public void onJoin(PreLoginEvent event) {
        int maxPerIP = plugin.getConfigFile().getSettings().getMaxJoinPerIP();
        if (maxPerIP <= 0 || !event.getResult().isAllowed())
            return;

        tracker.setPendingTimeoutMillis(pendingTimeout());
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        List<String> online = new ArrayList<>();
        for (Player player : plugin.getServer().getAllPlayers()) {
            if (IpAddressNormalizer.sameAddress(player.getRemoteAddress().getAddress().getHostAddress(), ip)) {
                online.add(player.getUsername());
            }
        }

        if (!tracker.tryAdmit(event.getUsername(), ip, maxPerIP, online, System.currentTimeMillis())) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.join(JoinConfiguration.newlines(),
                            ChatUtil.replacePlaceholders(
                                    plugin.getLangFile().getMessages().getKickMaxConnectionsPerIP(),
                                    new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix())))));
        }
    }

    @Subscribe(order = PostOrder.LAST)
    public void onLoginResult(LoginEvent event) {
        if (!event.getResult().isAllowed()) {
            tracker.release(event.getPlayer().getUsername());
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        tracker.joined(event.getPlayer().getUsername());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        tracker.release(event.getPlayer().getUsername());
    }
}
