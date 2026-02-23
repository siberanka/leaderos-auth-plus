package net.leaderos.auth.bukkit.command;

import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.BaseCommand;
import dev.triumphteam.cmd.core.annotation.Command;
import dev.triumphteam.cmd.core.annotation.Default;
import dev.triumphteam.cmd.core.annotation.SubCommand;
import dev.triumphteam.cmd.core.annotation.Optional;
import dev.triumphteam.cmd.core.annotation.Suggestion;
import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.bukkit.configuration.Language;
import net.leaderos.auth.bukkit.helpers.ChatUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.stream.Collectors;

@Command("alt")
@Permission("leaderos.auth.alt")
public class AltCommand extends BaseCommand {

    private final Bukkit plugin;

    public AltCommand(Bukkit plugin) {
        this.plugin = plugin;
    }

    @Default
    public void defaultCommand(CommandSender sender, @Optional @Suggestion("players") String playerName) {
        if (playerName == null) {
            ChatUtil.sendMessage(sender, plugin.getLangFile().getMessages().getAlt().getCmdParamError());
            return;
        }

        plugin.getFoliaLib().getScheduler().runAsync((task) -> {
            Language.Messages.Alt altConfig = plugin.getLangFile().getMessages().getAlt();
            List<String> alts = plugin.getDatabase().getAltsByName(playerName);

            if (alts == null || alts.isEmpty()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        altConfig.getCmdPlayerNoAlts().replace("{player}", playerName)));
            } else {
                String formattedAlts = alts.stream()
                        .map(acc -> altConfig.getCmdPlayerList().replace("{player}", acc))
                        .collect(Collectors.joining(altConfig.getCmdPlayerSeparator()));

                String msg = altConfig.getCmdPlayer().replace("{player}", playerName) + formattedAlts;
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            }
        });
    }

    @SubCommand("delete")
    @Permission("leaderos.auth.alt.delete")
    public void deleteCommand(CommandSender sender, @Suggestion("players") String playerName) {
        plugin.getFoliaLib().getScheduler().runAsync((task) -> {
            Language.Messages.Alt altConfig = plugin.getLangFile().getMessages().getAlt();
            int deleted = plugin.getDatabase().deletePlayerAlts(playerName);

            if (deleted == 0) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        altConfig.getCmdDeletedNotFound().replace("{player}", playerName)));
            } else if (deleted == 1) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        altConfig.getCmdDeletedSingular().replace("{amount}", String.valueOf(deleted))));
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        altConfig.getCmdDeletedPlural().replace("{amount}", String.valueOf(deleted))));
            }
        });
    }
}
