package net.leaderos.auth.bungee.listener;

import lombok.RequiredArgsConstructor;
import net.leaderos.auth.bungee.Bungee;
import net.leaderos.auth.shared.Shared;
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
        ProxiedPlayer player = event.getPlayer();
        plugin.getAuthenticatedPlayers().remove(player.getName());
    }

    @EventHandler
    public void onCommand(ChatEvent event) {
        if (event.isCancelled() || !event.isCommand()) return;
        if (!(event.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (plugin.getAuthenticatedPlayers().getOrDefault(player.getName(), false)) return;

        if (!plugin.getConfigFile().getSettings().getAllowedCommands().contains(command)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (event.isCancelled() || event.isCommand()) return;
        if (!(event.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (plugin.getAuthenticatedPlayers().getOrDefault(player.getName(), false)) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onConnect(ServerConnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (plugin.getAuthenticatedPlayers().getOrDefault(player.getName(), false)) return;

        String authServer = plugin.getConfigFile().getSettings().getAuthServer();
        if (event.getTarget().getName().equals(authServer)) return;

        Shared.getDebugAPI().send("Player tried to connect to a server different than the auth server. " +
                "Redirecting player " + player.getName() + " to auth server: " + authServer, true);
        event.setTarget(plugin.getProxy().getServerInfo(authServer));
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getSender() instanceof ProxiedPlayer)) return;
        if (!plugin.getConfigFile().getSettings().isHideTabComplete()) return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (plugin.getAuthenticatedPlayers().getOrDefault(player.getName(), false)) return;

        // Filter suggestions to only include allowed commands
        String cursor = event.getCursor().toLowerCase();
        List<String> allowedCommands = plugin.getConfigFile().getSettings().getTabCompleteAllowedCommands();

        if (cursor.startsWith("/")) {
            // Player is typing a command, filter suggestions
            String partial = cursor.substring(1).split(" ")[0];
            List<String> filtered = allowedCommands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(partial))
                    .map(cmd -> "/" + cmd)
                    .collect(Collectors.toList());
            event.getSuggestions().clear();
            event.getSuggestions().addAll(filtered);
        } else {
            // Not a command, clear all suggestions
            event.getSuggestions().clear();
        }
    }

}
