package net.leaderos.auth.bukkit.helpers;

import net.leaderos.auth.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class AltAccountManager {

    private final Bukkit plugin;
    private final DiscordWebhook webhook;

    public AltAccountManager(Bukkit plugin) {
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

        boolean added = plugin.getAltDataConfig().addAccountToIp(ip, player.getName());

        if (added) {
            plugin.getAltDataConfig().save();
        }

        // Check for alts
        List<String> accounts = plugin.getAltDataConfig().getAccountsByIp(ip);
        if (accounts.size() > 1) {
            // Player has alts on this IP
            StringBuilder content = new StringBuilder();
            content.append("IP Address: ").append(ip).append("\\n");
            content.append("Accounts: ");
            for (String acc : accounts) {
                content.append(acc).append(", ");
            }
            // Remove lingering comma
            if (content.length() > 2) {
                content.setLength(content.length() - 2);
            }

            // Fire Discord webhook
            webhook.sendAltMessage(content.toString(), player);
        }
    }

    /**
     * Increments the registration count for the given IP and saves the config.
     */
    public void incrementRegistration(String ip) {
        if (plugin.getConfigFile().getSettings().getRegisterLimit().isEnabled()) {
            plugin.getAltDataConfig().addRegistration(ip);
            plugin.getAltDataConfig().save();
        }
    }

    /**
     * Checks if the given IP has reached the registration limit.
     */
    public boolean hasReachedLimit(String ip) {
        if (!plugin.getConfigFile().getSettings().getRegisterLimit().isEnabled())
            return false;
        int max = plugin.getConfigFile().getSettings().getRegisterLimit().getMaxAccountsPerIp();
        return plugin.getAltDataConfig().hasReachedRegistrationLimit(ip, max);
    }

    public DiscordWebhook getWebhook() {
        return webhook;
    }
}
