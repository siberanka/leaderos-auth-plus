package net.leaderos.auth.bukkit.command;

import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.BaseCommand;
import dev.triumphteam.cmd.core.annotation.Command;
import dev.triumphteam.cmd.core.annotation.Default;
import dev.triumphteam.cmd.core.annotation.SubCommand;
import dev.triumphteam.cmd.core.annotation.Optional;
import dev.triumphteam.cmd.core.annotation.Suggestion;
import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.bukkit.helpers.ChatUtil;
import net.leaderos.auth.shared.helpers.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

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
            ChatUtil.sendMessage(sender, plugin.getLangFile().getMessages().getCommand().getInvalidArgument());
            return;
        }

        plugin.getFoliaLib().getScheduler().runAsync((task) -> {
            List<String> alts = plugin.getDatabase().getAltsByName(playerName);

            if (alts == null || alts.isEmpty()) {
                ChatUtil.sendMessage(sender, ChatUtil.replacePlaceholders(
                        plugin.getLangFile().getMessages().getAlt().getCmdNoAlts(),
                        new Placeholder("{player}", playerName)));
            } else {
                ChatUtil.sendMessage(sender, ChatUtil.replacePlaceholders(
                        plugin.getLangFile().getMessages().getAlt().getCmdAltsList(),
                        new Placeholder("{player}", playerName),
                        new Placeholder("{alts}", String.join(", ", alts))));
            }
        });
    }

    @SubCommand("delete")
    @Permission("leaderos.auth.alt.delete")
    public void deleteCommand(CommandSender sender, @Suggestion("players") String playerName) {
        plugin.getFoliaLib().getScheduler().runAsync((task) -> {
            int deleted = plugin.getDatabase().deletePlayerAlts(playerName);
            if (deleted == 0) {
                ChatUtil.sendMessage(sender, ChatUtil.replacePlaceholders(
                        plugin.getLangFile().getMessages().getAlt().getCmdDeletedNotFound(),
                        new Placeholder("{player}", playerName)));
            } else if (deleted == 1) {
                ChatUtil.sendMessage(sender, ChatUtil.replacePlaceholders(
                        plugin.getLangFile().getMessages().getAlt().getCmdDeletedSingular(),
                        new Placeholder("{player}", playerName)));
            } else {
                ChatUtil.sendMessage(sender, ChatUtil.replacePlaceholders(
                        plugin.getLangFile().getMessages().getAlt().getCmdDeletedPlural(),
                        new Placeholder("{player}", playerName),
                        new Placeholder("{amount}", String.valueOf(deleted))));
            }
        });
    }
}
