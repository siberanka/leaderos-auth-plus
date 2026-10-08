package net.leaderos.auth.bungee.listener;

import lombok.RequiredArgsConstructor;
import net.leaderos.auth.bungee.Bungee;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.proxy.RequestedServers;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class PlayerListener implements Listener {

    private final Bungee plugin;

    @EventHandler
    public void onQuit(PlayerDisconnectEvent event) {
        plugin.forget(event.getPlayer());
    }

    @EventHandler
    public void onCommand(ChatEvent event) {
        if (event.isCancelled() || !event.isCommand())
            return;
        if (!(event.getSender() instanceof ProxiedPlayer))
            return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (plugin.isAuthenticated(player))
            return;

        if (!plugin.getConfigFile().getSettings().getAllowedCommands().contains(command)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (event.isCancelled() || event.isCommand())
            return;
        if (!(event.getSender() instanceof ProxiedPlayer))
            return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (plugin.isAuthenticated(player))
            return;

        event.setCancelled(true);
    }

    /**
     * Runs after every other plugin (priority 127) so the target it sees is the final one, e.g. the
     * server twilight-proxy routed a reconnected Bedrock player to. Unauthenticated players are held on
     * the auth server; the server they asked for is remembered and they return to it after the login.
     */
    @EventHandler(priority = (byte) 127)
    public void onConnect(ServerConnectEvent event) {
        if (event.isCancelled())
            return;

        ProxiedPlayer player = event.getPlayer();
        if (isJoin(event)) {
            plugin.getSessionListener().applyFirstConnection(player);
        }
        if (plugin.isAuthenticated(player))
            return;

        String authServer = plugin.getConfigFile().getSettings().getAuthServer();
        ServerInfo target = event.getTarget();
        if (target != null && RequestedServers.isAuthServer(target.getName(), authServer))
            return;

        ServerInfo auth = plugin.getProxy().getServerInfo(authServer);
        if (auth == null) {
            // Fail closed: without the auth server an unauthenticated player may not go anywhere.
            Shared.getDebugAPI().send("Auth server '" + authServer + "' does not exist; refusing to connect "
                    + player.getName() + " anywhere else.", true);
            event.setCancelled(true);
            return;
        }

        // The proxy's default server is not a choice of the player; send-after-auth decides as before.
        if (target != null && plugin.getConfigFile().getSettings().isReturnToRequestedServer()
                && !isDefaultServer(player, target)) {
            plugin.getReturnRouter().getRequested().remember(player.getUniqueId(), target.getName(),
                    System.currentTimeMillis());
        }

        Shared.getDebugAPI().send("Player tried to connect to a server different than the auth server. " +
                "Redirecting player " + player.getName() + " to auth server: " + authServer, false);
        event.setTarget(auth);
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (event.isCancelled())
            return;
        if (!(event.getSender() instanceof ProxiedPlayer))
            return;
        if (!plugin.getConfigFile().getSettings().isHideTabComplete())
            return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (plugin.isAuthenticated(player))
            return;

        // Filter suggestions to only include allowed commands
        String cursor = event.getCursor().toLowerCase();
        List<String> allowedCommands = plugin.getConfigFile().getSettings().getTabCompleteAllowedCommands();

        if (cursor.startsWith("/")) {
            // Player is typing a command, filter suggestions
            String partial = cursor.substring(1).split(" ")[0];
            List<String> filtered = allowedCommands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(partial))
                    .map(cmd -> cmd)
                    .collect(Collectors.toList());
            event.getSuggestions().clear();
            event.getSuggestions().addAll(filtered);
        } else {
            // Not a command, clear all suggestions
            event.getSuggestions().clear();
        }
    }

    private static boolean isDefaultServer(ProxiedPlayer player, ServerInfo target) {
        try {
            List<String> priorities = player.getPendingConnection().getListener().getServerPriority();
            return priorities != null && !priorities.isEmpty() && priorities.get(0).equalsIgnoreCase(target.getName());
        } catch (RuntimeException | LinkageError unknown) {
            return false;
        }
    }

    /** First connection after joining the proxy (Reason exists on BungeeCord 1.13 and later). */
    private static boolean isJoin(ServerConnectEvent event) {
        try {
            return event.getReason() == ServerConnectEvent.Reason.JOIN_PROXY;
        } catch (NoSuchMethodError | NoClassDefFoundError older) {
            return event.getPlayer().getServer() == null;
        }
    }

}
