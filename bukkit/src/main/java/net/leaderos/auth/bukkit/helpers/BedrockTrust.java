package net.leaderos.auth.bukkit.helpers;

import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.bukkit.configuration.Config;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.enums.SessionState;
import net.leaderos.auth.shared.model.response.GameSessionResponse;
import net.leaderos.auth.shared.security.BedrockLinkStore;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Passwordless login for Bedrock players whose account is bound to their Xbox identity (XUID).
 * The identity always comes from the Floodgate API for the player's UUID; names are never trusted.
 */
public class BedrockTrust {

    private final Bukkit plugin;
    /** Binding looked up during the async pre-login, keyed by the joining UUID. */
    private final Map<UUID, Prefetched> prefetched = new ConcurrentHashMap<>();

    public BedrockTrust(Bukkit plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfigFile().getSettings().getBedrock().isTrustXbox()
                && BedrockSupport.isFloodgateAvailable();
    }

    /**
     * Reads the binding of an account during the async pre-login so the join stays non-blocking.
     */
    public void prefetch(UUID uuid, String name) {
        prefetched.remove(uuid);
        long now = System.currentTimeMillis();
        // Logins that never reached the join leave nothing behind for long.
        prefetched.values().removeIf(entry -> now - entry.created > 60_000L);
        if (!isEnabled() || plugin.getDatabase() == null || plugin.getDatabase().getBedrockLinkStore() == null) {
            return;
        }
        BedrockLinkStore.Link link = plugin.getDatabase().getBedrockLinkStore().find(name);
        if (link != null) {
            prefetched.put(uuid, new Prefetched(name.toLowerCase(Locale.ROOT), link, now));
        }
    }

    /**
     * Decides at join whether the player may skip the password.
     *
     * @return true when the player is a verified Floodgate player bound to this account
     */
    public boolean tryTrust(Player player, GameSessionResponse session) {
        Prefetched entry = prefetched.remove(player.getUniqueId());
        if (entry == null || session == null || !isEnabled()) {
            return false;
        }
        // Only registered accounts that would otherwise need their password; never registration.
        if (session.getState() != SessionState.LOGIN_REQUIRED) {
            return false;
        }
        if (!entry.account.equals(player.getName().toLowerCase(Locale.ROOT))) {
            return false;
        }
        String xuid = floodgateXuid(player);
        long maxAge = TimeUnit.DAYS.toMillis(maxAgeDays());
        if (xuid == null || !entry.link.trusts(xuid, maxAge, System.currentTimeMillis())) {
            return false;
        }
        Shared.getDebugAPI().send(player.getName() + " logged in through the Xbox account bound to it.", false);
        return true;
    }

    /**
     * Binds the account to the player's XUID after a password (and TFA) verified login or a registration.
     */
    public void rememberVerified(Player player) {
        if (!isEnabled() || plugin.getDatabase() == null || plugin.getDatabase().getBedrockLinkStore() == null) {
            return;
        }
        String xuid = floodgateXuid(player);
        if (xuid == null) {
            return;
        }
        String name = player.getName();
        BedrockLinkStore store = plugin.getDatabase().getBedrockLinkStore();
        plugin.getFoliaLib().getScheduler().runAsync((task) -> {
            BedrockLinkStore.BindResult result = store.bind(name, xuid, System.currentTimeMillis());
            if (result == BedrockLinkStore.BindResult.CONFLICT) {
                plugin.getLogger().warning(name + " logged in with its password from an Xbox account that is not "
                        + "the one bound to it; passwordless Bedrock login stays with the original Xbox account. "
                        + "Use /leaderosauth unlinkbedrock " + name + " if the owner changed Xbox accounts.");
            } else if (result == BedrockLinkStore.BindResult.BOUND) {
                ChatUtil.sendConsoleInfo(name + " is now bound to its Xbox account for Bedrock logins.");
            }
        });
    }

    public void forget(UUID uuid) {
        prefetched.remove(uuid);
    }

    private int maxAgeDays() {
        Config.Settings.Bedrock bedrock = plugin.getConfigFile().getSettings().getBedrock();
        return Math.max(1, Math.min(365, bedrock.getTrustMaxAgeDays()));
    }

    /**
     * @return the XUID Floodgate verified for this player, or null for Java players
     */
    public static String floodgateXuid(Player player) {
        if (!BedrockSupport.isFloodgateAvailable()) {
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

    private static final class Prefetched {
        private final String account;
        private final BedrockLinkStore.Link link;
        private final long created;

        private Prefetched(String account, BedrockLinkStore.Link link, long created) {
            this.account = account;
            this.link = link;
            this.created = created;
        }
    }
}
