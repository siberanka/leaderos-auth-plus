package net.leaderos.auth.bukkit.command;

import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.BaseCommand;
import dev.triumphteam.cmd.core.annotation.Command;
import dev.triumphteam.cmd.core.annotation.Default;
import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.bukkit.helpers.ChatUtil;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.helpers.Placeholder;
import net.leaderos.auth.shared.helpers.UrlUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command(value = "leaderosauth:losauthreload", alias = { "losauthreload" })
@Permission("leaderosauth.reload")
public class ReloadCommand extends BaseCommand {

    private final Bukkit plugin;

    public ReloadCommand(Bukkit plugin) {
        this.plugin = plugin;
    }

    @Default
    public void defaultCommand(CommandSender sender) {
        // Core Reloads (uses Okaeri broken-file rescue mechanisms)
        plugin.setupFiles();

        // Subsystems
        Shared.setLink(UrlUtil.format(plugin.getConfigFile().getSettings().getUrl()));
        Shared.setApiKey(plugin.getConfigFile().getSettings().getApiKey());

        // Rebooting database references for IP connection / Alt limits
        plugin.getFoliaLib().getScheduler().runAsync((task) -> {
            if (plugin.getDatabase() != null) {
                plugin.getDatabase().closeDataSource();
                plugin.setupDatabase();
            }
        });

        // Kicking players that haven't verified a valid session state according to the
        // server local map
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.isAuthenticated(player)) {
                plugin.getFoliaLib().getScheduler().runLater(() -> {
                    player.kickPlayer(String.join("\n",
                            ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickAnError(),
                                    new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));
                }, 1L);
            }
        }

        ChatUtil.sendMessage(sender, plugin.getLangFile().getMessages().getReload());
    }
}
