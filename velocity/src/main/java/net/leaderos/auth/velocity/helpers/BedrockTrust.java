package net.leaderos.auth.velocity.helpers;

import com.velocitypowered.api.proxy.Player;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.enums.SessionState;
import net.leaderos.auth.shared.model.response.GameSessionResponse;
import net.leaderos.auth.shared.security.BedrockLinkStore;
import net.leaderos.auth.velocity.Velocity;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.concurrent.TimeUnit;

/**
 * Passwordless login for Bedrock players whose account is bound to their Xbox identity (XUID).
 * The identity always comes from the Floodgate API for the player's UUID; names are never trusted.
 */
public final class BedrockTrust {

    private final Velocity plugin;

    public BedrockTrust(Velocity plugin) {
        this.plugin = plugin;
    }

    public static boolean isFloodgateAvailable() {
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            return Velocity.getInstance().getServer().getPluginManager().isLoaded("floodgate");
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static boolean isBedrockPlayer(Player player) {
        return floodgateXuid(player) != null;
    }

    public boolean isEnabled() {
        return plugin.getConfigFile().getSettings().getBedrock().isTrustXbox() && isFloodgateAvailable()
                && store() != null;
    }

    /**
     * @return true when the player is a verified Floodgate player bound to this registered account
     */
    public boolean trusts(Player player, GameSessionResponse session) {
        if (!isEnabled() || session == null || session.getState() != SessionState.LOGIN_REQUIRED) {
            return false;
        }
        String xuid = floodgateXuid(player);
        if (xuid == null) {
            return false;
        }
        BedrockLinkStore.Link link = store().find(player.getUsername());
        long maxAge = TimeUnit.DAYS.toMillis(maxAgeDays());
        boolean trusted = link != null && link.trusts(xuid, maxAge, System.currentTimeMillis());
        if (trusted) {
            Shared.getDebugAPI().send(player.getUsername() + " logged in through the Xbox account bound to it.", false);
        }
        return trusted;
    }

    /**
     * Binds the account to the player's XUID after a password (and TFA) verified login or a registration.
     */
    public void rememberVerified(Player player) {
        if (!isEnabled()) {
            return;
        }
        String xuid = floodgateXuid(player);
        if (xuid == null) {
            return;
        }
        String name = player.getUsername();
        BedrockLinkStore store = store();
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            BedrockLinkStore.BindResult result = store.bind(name, xuid, System.currentTimeMillis());
            if (result == BedrockLinkStore.BindResult.CONFLICT) {
                plugin.getLogger().warn(name + " logged in with its password from an Xbox account that is not the "
                        + "one bound to it; passwordless Bedrock login stays with the original Xbox account. Use "
                        + "/leaderosauth unlinkbedrock " + name + " if the owner changed Xbox accounts.");
            } else if (result == BedrockLinkStore.BindResult.BOUND) {
                ChatUtil.sendConsoleInfo(name + " is now bound to its Xbox account for Bedrock logins.");
            }
        }).schedule();
    }

    private BedrockLinkStore store() {
        return plugin.getDatabase() == null ? null : plugin.getDatabase().getBedrockLinkStore();
    }

    private int maxAgeDays() {
        return Math.max(1, Math.min(365, plugin.getConfigFile().getSettings().getBedrock().getTrustMaxAgeDays()));
    }

    /**
     * @return the XUID Floodgate verified for this player, or null for Java players
     */
    public static String floodgateXuid(Player player) {
        if (player == null || !isFloodgateAvailable()) {
            return null;
        }
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            if (!api.isFloodgatePlayer(player.getUniqueId())) {
                return null;
            }
            FloodgatePlayer floodgatePlayer = api.getPlayer(player.getUniqueId());
            if (floodgatePlayer == null) {
                return null;
            }
            String xuid = floodgatePlayer.getXuid();
            return BedrockLinkStore.isValidXuid(xuid) ? xuid : null;
        } catch (Exception | LinkageError unavailable) {
            return null;
        }
    }
}
