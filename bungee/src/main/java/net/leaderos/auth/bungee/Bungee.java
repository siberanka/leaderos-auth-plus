package net.leaderos.auth.bungee;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bungee.YamlBungeeConfigurer;
import lombok.Getter;
import net.leaderos.auth.bungee.configuration.Config;
import net.leaderos.auth.bungee.helpers.BedrockDatabase;
import net.leaderos.auth.bungee.helpers.DebugBungee;
import net.leaderos.auth.bungee.listener.AuthMessageListener;
import net.leaderos.auth.bungee.listener.IpConnectionLimitListener;
import net.leaderos.auth.bungee.listener.PlayerListener;
import net.leaderos.auth.bungee.listener.PluginMessageListener;
import net.leaderos.auth.bungee.listener.SessionListener;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.helpers.PluginUpdater;
import net.leaderos.auth.shared.helpers.UrlUtil;
import net.leaderos.auth.shared.messaging.AuthChannel;
import net.leaderos.auth.shared.messaging.ReplayGuard;
import net.leaderos.auth.shared.messaging.SecretDiscovery;
import net.leaderos.auth.shared.proxy.RequestedServers;
import net.leaderos.auth.shared.proxy.ReturnRouter;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.bstats.bungeecord.Metrics;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Getter
public class Bungee extends Plugin {

    @Getter
    private static Bungee instance;
    /**
     * Authenticated connections by lowercase name. The value is the connection itself, so an entry can
     * never carry over to a later connection that uses the same name.
     */
    private final Map<String, ProxiedPlayer> authenticatedPlayers = new ConcurrentHashMap<>();
    private final ReplayGuard replayGuard = new ReplayGuard();
    private Shared shared;
    private Config configFile;
    private volatile List<AuthChannel.Key> messagingKeys = Collections.emptyList();
    private ReturnRouter returnRouter;
    private SessionListener sessionListener;
    private BedrockDatabase bedrockDatabase;
    private AuthMessageListener authMessageListener;

    @Override
    public void onEnable() {
        instance = this;

        setupFiles();

        shared = new Shared(UrlUtil.format(configFile.getSettings().getUrl()), configFile.getSettings().getApiKey(),
                new DebugBungee());

        new Metrics(this, 26805);

        returnRouter = new ReturnRouter(new RequestedServers(TimeUnit.SECONDS.toMillis(
                configFile.getSettings().getRequestedServerTtlSeconds())),
                configFile.getSettings().isReturnToRequestedServer());
        loadMessagingKeys();
        setupBedrockDatabase();

        getProxy().registerChannel(AuthChannel.CHANNEL);
        sessionListener = new SessionListener(this);
        this.getProxy().getPluginManager().registerListener(this, sessionListener);
        authMessageListener = new AuthMessageListener(this);
        this.getProxy().getPluginManager().registerListener(this, authMessageListener);
        this.getProxy().getPluginManager().registerListener(this, new PluginMessageListener(this));
        this.getProxy().getPluginManager().registerListener(this, new PlayerListener(this));
        this.getProxy().getPluginManager().registerListener(this, new IpConnectionLimitListener(this));

        getProxy().getScheduler().schedule(this, () -> {
            long now = System.currentTimeMillis();
            returnRouter.getRequested().sweep(now);
            sessionListener.sweep(now);
        }, 1, 1, TimeUnit.MINUTES);

        String authServerName = configFile.getSettings().getAuthServer();
        ServerInfo serverInfo = getProxy().getServerInfo(authServerName);
        if (serverInfo == null) {
            getLogger().severe("Auth server '" + authServerName + "' not found. Please check your config.yml.");
        }
    }

    @Override
    public void onDisable() {
        if (bedrockDatabase != null) {
            bedrockDatabase.close();
        }
        getProxy().unregisterChannel(AuthChannel.CHANNEL);
    }

    public boolean isAuthenticated(ProxiedPlayer player) {
        return player != null && authenticatedPlayers.get(key(player)) == player;
    }

    public void setAuthenticated(ProxiedPlayer player, boolean authenticated) {
        if (player == null) {
            return;
        }
        if (authenticated) {
            authenticatedPlayers.put(key(player), player);
        } else {
            authenticatedPlayers.remove(key(player), player);
        }
    }

    /** Forgets everything about a connection that ended. */
    public void forget(ProxiedPlayer player) {
        authenticatedPlayers.remove(key(player), player);
        returnRouter.forget(player.getUniqueId());
        sessionListener.forget(player.getUniqueId());
    }

    public boolean isOnAuthServer(ProxiedPlayer player) {
        return player.getServer() != null && RequestedServers.isAuthServer(
                player.getServer().getInfo().getName(), configFile.getSettings().getAuthServer());
    }

    private static String key(ProxiedPlayer player) {
        return player.getName().toLowerCase(Locale.ROOT);
    }

    private void loadMessagingKeys() {
        Config.Settings.Messaging messaging = configFile.getSettings().getMessaging();
        String configured = messaging.getSecret();
        if (configured != null && !configured.trim().isEmpty() && !AuthChannel.isUsableSecret(configured)) {
            getLogger().warning("messaging.secret is shorter than " + AuthChannel.MIN_SECRET_LENGTH
                    + " characters and is ignored.");
        }
        messagingKeys = SecretDiscovery.keys(SecretDiscovery.bungee(
                Paths.get("").toAbsolutePath(), configured));
        if (!messaging.isRequireSignature()) {
            getLogger().warning("messaging.require-signature is false: unsigned login messages from backends are "
                    + "accepted. Use this only while upgrading the backends.");
        } else if (messagingKeys.isEmpty()) {
            getLogger().severe("No messaging secret: login messages from the auth server cannot be verified and "
                    + "are refused, so players stay on the auth server after logging in. Set messaging.secret "
                    + "here and settings.proxy-messaging.secret on the backends to the same value (16+ "
                    + "characters), or install BungeeGuard.");
        }
    }

    private void setupBedrockDatabase() {
        Config.Settings.Bedrock bedrock = configFile.getSettings().getBedrock();
        if (!bedrock.isTrustXbox()) {
            return;
        }
        int maxAge = Math.max(1, Math.min(365, bedrock.getTrustMaxAgeDays()));
        if (maxAge != bedrock.getTrustMaxAgeDays()) {
            getLogger().warning("bedrock.trust-max-age-days must be between 1 and 365; using " + maxAge + ".");
            bedrock.setTrustMaxAgeDays(maxAge);
        }
        if (getProxy().getPluginManager().getPlugin("floodgate") == null) {
            getLogger().warning("bedrock.trust-xbox needs Floodgate on this proxy; Bedrock players use the auth "
                    + "server as usual.");
            return;
        }
        BedrockDatabase database = new BedrockDatabase(this);
        if (database.initialize()) {
            bedrockDatabase = database;
            getLogger().info("Bedrock Xbox trust is enabled (MySQL).");
        } else {
            database.close();
            getLogger().severe("Bedrock Xbox trust is disabled: the database could not be initialized.");
        }
    }

    public void setupFiles() {
        try {
            File configYml = new File(this.getDataFolder().getAbsolutePath(), "config.yml");
            this.configFile = loadConfigWithRecovery(configYml);
            Config.Settings settings = this.configFile.getSettings();
            settings.setSessionCheckTimeoutMillis(Math.max(500, Math.min(5000, settings.getSessionCheckTimeoutMillis())));
            settings.setRequestedServerTtlSeconds(Math.max(30, Math.min(3600, settings.getRequestedServerTtlSeconds())));
            String prefix = settings.getBedrock().getDatabase().getPrefix();
            if (prefix == null || !prefix.matches("[A-Za-z0-9_]{1,32}")) {
                getLogger().warning("Unsafe database table prefix rejected; using leaderos_auth_.");
                settings.getBedrock().getDatabase().setPrefix("leaderos_auth_");
            }
            this.configFile.save();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to load config.yml!", exception);
        }
    }

    private Config loadConfigWithRecovery(File file) {
        try {
            return ConfigManager.create(Config.class, (it) -> {
                it.withConfigurer(new YamlBungeeConfigurer());
                it.withBindFile(file);
                it.withRemoveOrphans(true);
                it.saveDefaults();
                it.load(true);
            });
        } catch (Exception e) {
            if (file.exists()) {
                File broken = new File(file.getParent(), file.getName().replace(".yml", ".broken.yml"));
                if (broken.exists())
                    broken.delete();
                file.renameTo(broken);
                getLogger().warning("Config file " + file.getName() + " was corrupted! Backed up to " + broken.getName()
                        + " and recreated with defaults.");
            }
            return ConfigManager.create(Config.class, (it) -> {
                it.withConfigurer(new YamlBungeeConfigurer());
                it.withBindFile(file);
                it.withRemoveOrphans(true);
                it.saveDefaults();
                it.load(true);
            });
        }
    }

    public void checkUpdate() {
        Bungee.getInstance().getProxy().getScheduler().runAsync(Bungee.getInstance(), () -> {
            PluginUpdater updater = new PluginUpdater(getDescription().getVersion());
            try {
                if (updater.checkForUpdates()) {
                    getLogger().log(Level.WARNING,
                            "There is a new update available for LeaderOS Auth Plugin! Please update to "
                                    + updater.getLatestVersion());
                }
            } catch (Exception ignored) {
            }
        });
    }

}
