package net.leaderos.auth.velocity.helpers;

import com.velocitypowered.api.proxy.Player;
import net.leaderos.auth.velocity.Velocity;
import net.leaderos.auth.velocity.configuration.Language;

import java.util.List;
import java.util.stream.Collectors;
import net.leaderos.auth.shared.security.RegistrationDecision;
import net.leaderos.auth.velocity.configuration.Config;

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

        if (plugin.getDatabase() == null) {
            return;
        }

        if (!plugin.getConfigFile().getSettings().getRegisterLimit().isEnabled()
                && !plugin.getConfigFile().getSettings().getAltTracker().isEnabled()) {
            return;
        }

        final String uuid = player.getUniqueId().toString();
        final String name = player.getUsername();
        final boolean notificationsEnabled = plugin.getConfigFile().getSettings().getAltTracker().isEnabled()
                && !player.hasPermission("leaderos.auth.alt.exempt");

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            plugin.getDatabase().recordAuthenticatedAccount(uuid, name, ip);

            if (!notificationsEnabled) {
                return;
            }

            plugin.getDatabase().addOrUpdatePlayer(uuid, name);
            plugin.getDatabase().addOrUpdateIp(ip, uuid);

            // Check for alts
            List<String> accounts = plugin.getDatabase().getSecurityNetworkAccountNames(ip, name);
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
                    player.getUniqueId().toString(), player.getUsername(), ip);
        }
        return plugin.getDatabase().commitRegistration(token,
                player.getUniqueId().toString(), player.getUsername(), ip);
    }

    public void cancelRegistration(String token) {
        if (plugin.getDatabase() != null) {
            plugin.getDatabase().releaseRegistration(token);
        }
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
