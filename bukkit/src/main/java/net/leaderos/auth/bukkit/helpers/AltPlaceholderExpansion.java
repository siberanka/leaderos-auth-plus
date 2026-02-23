package net.leaderos.auth.bukkit.helpers;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.leaderos.auth.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AltPlaceholderExpansion extends PlaceholderExpansion {

    private final Bukkit plugin;

    public AltPlaceholderExpansion(Bukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "leaderosauth";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {
        if (!plugin.getConfigFile().getSettings().getDatabase().isPlaceholderEnabled()) {
            return null;
        }

        // %leaderosauth_altdetector_alts_<player>%
        if (identifier.startsWith("altdetector_alts_")) {
            String targetPlayerName = identifier.substring(17); // Length of "altdetector_alts_"
            List<String> alts = plugin.getDatabase().getAltsByName(targetPlayerName);

            if (alts == null || alts.isEmpty()) {
                return "None";
            }
            String separator = plugin.getConfigFile().getSettings().getDatabase().getPlaceholderSeparator();
            return String.join(separator, alts);
        }

        return null;
    }
}
