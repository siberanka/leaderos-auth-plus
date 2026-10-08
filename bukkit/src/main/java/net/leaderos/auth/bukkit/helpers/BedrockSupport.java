package net.leaderos.auth.bukkit.helpers;

import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bedrock helpers that are safe to call when Floodgate is not installed. Classes that touch the
 * form library (Cumulus) must only be loaded after {@link #isBedrockPlayer(Player)} returned true;
 * otherwise loading them fails and breaks every join.
 */
public final class BedrockSupport {

    static final Set<UUID> PENDING_FORMS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    static final ConcurrentHashMap<UUID, Long> LAST_SUBMIT = new ConcurrentHashMap<>();

    private BedrockSupport() {
    }

    public static boolean isFloodgateAvailable() {
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            return org.bukkit.Bukkit.getPluginManager().getPlugin("floodgate") != null;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static boolean isBedrockPlayer(Player player) {
        if (!isFloodgateAvailable())
            return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    /** Clears form locks and cooldowns of a player. */
    public static void cleanup(Player player) {
        PENDING_FORMS.remove(player.getUniqueId());
        LAST_SUBMIT.remove(player.getUniqueId());
    }
}
