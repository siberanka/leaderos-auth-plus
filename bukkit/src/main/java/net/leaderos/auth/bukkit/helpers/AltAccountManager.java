package net.leaderos.auth.bukkit.helpers;

import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.bukkit.configuration.Language;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import java.util.List;
import java.util.stream.Collectors;

public class AltAccountManager {

    private final Bukkit plugin;
    private final DiscordWebhook webhook;
    private boolean superVanishEnabled = false;

    public AltAccountManager(Bukkit plugin) {
        this.plugin = plugin;
        this.webhook = new DiscordWebhook(plugin);
        this.superVanishEnabled = plugin.getServer().getPluginManager().getPlugin("SuperVanish") != null
                || plugin.getServer().getPluginManager().getPlugin("PremiumVanish") != null;
    }

    /**
     * Called after successful login or register.
     * Records the player's IP and checks for alt accounts.
     * Respects the leaderos.auth.alt.exempt permission.
     */
    public void processPlayerRecord(Player player, String ip) {
        if (ip == null || ip.isEmpty())
            return;

        // Skip if alt logger is disabled
        if (!plugin.getConfigFile().getSettings().getDatabase().isAltLoggerEnabled()) {
            return;
        }

        // Skip if player is exempt
        if (player.hasPermission("leaderos.auth.alt.exempt")) {
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String uuid = player.getUniqueId().toString();
            String name = player.getName();

            plugin.getDatabase().addOrUpdatePlayer(uuid, name);
            plugin.getDatabase().addOrUpdateIp(ip, uuid);

            // Check for alts
            List<String> accounts = plugin.getDatabase().getAltNames(uuid);
            if (!accounts.isEmpty()) {
                Language.Messages.Alt altConfig = plugin.getLangFile().getMessages().getAlt();

                // Format alt list using configurable format and separator
                String listFormat = altConfig.getJoinPlayerList();
                String separator = altConfig.getJoinPlayerSeparator();

                String formattedAlts = accounts.stream()
                        .map(acc -> listFormat.replace("{player}", acc))
                        .collect(Collectors.joining(separator));

                // Build notification string: prefix + joinPlayer + formattedAlts
                String notifyStr = altConfig.getJoinPlayerPrefix()
                        + altConfig.getJoinPlayer().replace("{player}", name)
                        + formattedAlts;

                // Build content for Discord (clean, no color codes)
                String cleanNotify = ChatColor.stripColor(
                        ChatColor.translateAlternateColorCodes('&', notifyStr));

                // Fire Discord webhook
                webhook.sendAltMessage(cleanNotify, player);

                // Back to main thread for notifying online players
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String coloredMsg = ChatColor.translateAlternateColorCodes('&', notifyStr);

                    // Log to console
                    plugin.getLogger().info(ChatColor.stripColor(coloredMsg));

                    // Notify online players with permission
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        if (p.hasPermission("leaderos.auth.alt.notify")) {
                            if (p.hasPermission("leaderos.auth.alt.notify.seevanished") || !isVanished(player, p)) {
                                p.sendMessage(coloredMsg);
                            }
                        }
                    }
                });
            }
        });
    }

    /**
     * Checks if a player is vanished. Supports PremiumVanish/SuperVanish
     * via VanishAPI, and falls back to metadata check.
     */
    private boolean isVanished(Player player, Player recipient) {
        if (player == null)
            return false;

        if (superVanishEnabled) {
            try {
                Class<?> vanishApiClass = Class.forName("de.myzelyam.api.vanish.VanishAPI");
                Object result = vanishApiClass.getMethod("canSee", Player.class, Player.class)
                        .invoke(null, recipient, player);
                if (result instanceof Boolean) {
                    return !((Boolean) result); // !canSee = vanished
                }
            } catch (Exception ignored) {
                // Fall through to metadata check
            }
        }

        // Fallback: standard metadata check
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Increments the registration count for the given IP and saves the config.
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

    public DiscordWebhook getWebhook() {
        return webhook;
    }
}
