package net.leaderos.auth.bukkit.helpers;

import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.bukkit.configuration.Language;
import net.leaderos.auth.bukkit.configuration.Config;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import net.leaderos.auth.shared.security.RegistrationDecision;

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

        if (plugin.getDatabase() == null) {
            return;
        }

        if (!plugin.getConfigFile().getSettings().getRegisterLimit().isEnabled()
                && !plugin.getConfigFile().getSettings().getAltTracker().isEnabled()) {
            return;
        }

        final String uuid = player.getUniqueId().toString();
        final String name = player.getName();
        final boolean notificationsEnabled = plugin.getConfigFile().getSettings().getAltTracker().isEnabled()
                && !player.hasPermission("leaderos.auth.alt.exempt");

        plugin.getFoliaLib().getScheduler().runAsync((task) -> {
            // Security history must never depend on notification settings or bypass
            // permissions. This link is what stops IP rotation from resetting the limit.
            plugin.getDatabase().recordAuthenticatedAccount(uuid, name, ip);

            if (!notificationsEnabled) {
                return;
            }

            plugin.getDatabase().addOrUpdatePlayer(uuid, name);
            plugin.getDatabase().addOrUpdateIp(ip, uuid);

            List<String> accounts = plugin.getDatabase().getSecurityNetworkAccountNames(ip, name);
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
                plugin.getFoliaLib().getScheduler().runNextTick((notifyTask) -> {
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

    public RegistrationDecision reserveRegistration(String ip, String playerName) {
        Config.Settings.RegisterLimit limit = plugin.getConfigFile().getSettings().getRegisterLimit();
        if (!limit.isEnabled()) {
            return RegistrationDecision.allowed(null, 0, java.util.Collections.emptyList());
        }
        if (plugin.getDatabase() == null) {
            return RegistrationDecision.denied(RegistrationDecision.Status.SECURITY_ERROR, 0,
                    java.util.Collections.emptyList());
        }
        return plugin.getDatabase().reserveRegistration(ip, playerName,
                limit.getMaxAccountsPerIp(), limit.getReservationTimeoutSeconds());
    }

    public boolean completeRegistration(String token, Player player, String ip) {
        if (token == null && !plugin.getConfigFile().getSettings().getAltTracker().isEnabled()) {
            return true;
        }
        if (plugin.getDatabase() == null) {
            return false;
        }
        if (token == null) {
            return plugin.getDatabase().recordAuthenticatedAccount(
                    player.getUniqueId().toString(), player.getName(), ip);
        }
        return plugin.getDatabase().commitRegistration(token,
                player.getUniqueId().toString(), player.getName(), ip);
    }

    public void cancelRegistration(String token) {
        if (plugin.getDatabase() != null) {
            plugin.getDatabase().releaseRegistration(token);
        }
    }

    public DiscordWebhook getWebhook() {
        return webhook;
    }
}
