package net.leaderos.auth.bukkit.listener;

import lombok.RequiredArgsConstructor;
import net.leaderos.auth.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

@RequiredArgsConstructor
public class TabCompleteListener implements Listener {

    private final Bukkit plugin;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (plugin.isAuthenticated(player))
            return;

        // Filter all commands: only keep allowed auth commands
        event.getCommands().removeIf(command -> {
            // Strip namespace if present (e.g. "bukkit:version" -> "version")
            String baseCommand = command.contains(":") ? command.substring(command.indexOf(':') + 1) : command;
            return !plugin.getAllowedCommands().contains(baseCommand.toLowerCase());
        });
    }
}
