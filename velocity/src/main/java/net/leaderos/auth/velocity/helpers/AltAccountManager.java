package net.leaderos.auth.velocity.helpers;

import com.velocitypowered.api.proxy.Player;
import net.leaderos.auth.velocity.Velocity;
import net.leaderos.auth.velocity.configuration.Language;

import java.util.List;
import java.util.stream.Collectors;

public class AltAccountManager {

    private final Velocity plugin;
    private final DiscordWebhook webhook;

    public AltAccountManager(Velocity plugin) {
        this.plugin = plugin;
        this.webhook = new DiscordWebhook(plugin);
    }

    /**
     * Called after successful login or register.
     * Records the player's IP and checks for alt accounts.
     */
    public void processPlayerRecord(Player player, String ip) {
        if (ip == null || ip.isEmpty())
            return;

        // Skip if alt logger is disabled
        if (!plugin.getConfigFile().getSettings().getDatabase().isAltLoggerEnabled()) {
            return;
        }

        // Skip if player has exempt permission
        if (player.hasPermission("leaderos.auth.alt.exempt")) {
            return;
        }

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            String uuid = player.getUniqueId().toString();
            String name = player.getUsername();

            plugin.getDatabase().addOrUpdatePlayer(uuid, name);
            plugin.getDatabase().addOrUpdateIp(ip, uuid);

            // Check for alts
            List<String> accounts = plugin.getDatabase().getAltNames(uuid);
            if (!accounts.isEmpty()) {
                Language.Messages.Alt altConfig = plugin.getLangFile().getMessages().getAlt();

                String listFormat = altConfig.getJoinPlayerList();
                String separator = altConfig.getJoinPlayerSeparator();

                String formattedAlts = accounts.stream()
                        .map(acc -> listFormat.replace("{player}", acc))
                        .collect(Collectors.joining(separator));

                String notifyStr = altConfig.getJoinPlayerPrefix()
                        + altConfig.getJoinPlayer().replace("{player}", name)
                        + formattedAlts;

                // Strip color codes for Discord
                String cleanNotify = stripColorCodes(notifyStr);

                // Fire Discord webhook
                webhook.sendAltMessage(cleanNotify, player);

                // Log to console
                plugin.getLogger().info(stripColorCodes(notifyStr));

                // Notify online players with permission
                for (Player p : plugin.getServer().getAllPlayers()) {
                    if (p.hasPermission("leaderos.auth.alt.notify")) {
                        p.sendMessage(ChatUtil.color(notifyStr));
                    }
                }
            }
        }).schedule();
    }

    /**
     * Increments the registration count for the given IP.
     */
    public void incrementRegistration(String ip) {
        if (plugin.getConfigFile().getSettings().getRegisterLimit().isEnabled()) {
            plugin.getDatabase().incrementRegistration(ip);
        }
    }

    /**
     * Checks if the given IP has reached the registration limit.
     */
    public boolean hasReachedLimit(String ip) {
        if (!plugin.getConfigFile().getSettings().getRegisterLimit().isEnabled())
            return false;
        int max = plugin.getConfigFile().getSettings().getRegisterLimit().getMaxAccountsPerIp();
        return plugin.getDatabase().hasReachedRegistrationLimit(ip, max);
    }

    private String stripColorCodes(String text) {
        if (text == null)
            return "";
        return text.replaceAll("&[0-9a-fk-or]", "").replaceAll("§[0-9a-fk-or]", "");
    }

    public DiscordWebhook getWebhook() {
        return webhook;
    }
}
