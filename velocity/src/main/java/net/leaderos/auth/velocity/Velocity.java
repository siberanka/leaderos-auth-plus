package net.leaderos.auth.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import lombok.Getter;
import lombok.Setter;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.command.LimboCommandMeta;
import net.elytrium.limboapi.api.file.BuiltInWorldFileType;
import net.elytrium.limboapi.api.file.WorldFile;
import net.elytrium.limboapi.api.player.GameMode;
import net.kyori.adventure.text.Component;
import net.leaderos.auth.shared.helpers.Placeholder;
import net.leaderos.auth.shared.helpers.PluginUpdater;
import net.leaderos.auth.velocity.commands.LeaderOSCommand;
import net.leaderos.auth.velocity.configuration.Config;
import net.leaderos.auth.velocity.configuration.Language;
import net.leaderos.auth.velocity.helpers.ChatUtil;
import net.leaderos.auth.velocity.helpers.DebugVelocity;
import net.leaderos.auth.velocity.listener.ConnectionListener;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.helpers.UrlUtil;
import net.leaderos.auth.velocity.listener.IpConnectionLimitListener;
import net.leaderos.auth.velocity.database.Database;
import net.leaderos.auth.velocity.database.Mysql;
import net.leaderos.auth.velocity.database.Sqlite;
import net.leaderos.auth.velocity.helpers.AltAccountManager;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Main class of velocity
 */
@Getter
@Setter
@Plugin(id = "leaderosauth", name = "LeaderOS-Auth", version = "1.0.5-fork", url = "https://leaderos.net", description = "LeaderOS Auth for Velocity", authors = {
        "leaderos", "efekurbann", "siberanka" }, dependencies = { @Dependency(id = "limboapi") })
public class Velocity {

    /**
     * Instance of plugin
     */
    @Getter
    private static Velocity instance;
    /**
     * Instance of server
     */
    private final ProxyServer server;
    /**
     * Logger of server
     */
    private final Logger logger;

    /**
     * Data directory of server
     */
    private final Path dataDirectory;
    /**
     * bStats metrics
     */
    private final Metrics.Factory metricsFactory;
    /**
     * Config file of plugin
     */
    private Config configFile;
    /**
     * Lang file of plugin
     */
    private Language langFile;
    @Getter
    private CommandManager commandManager;
    /**
     * Shared holder
     */
    private Shared shared;
    @Getter
    private LimboFactory factory;
    @Getter
    private Limbo limboServer;
    @Getter
    private Database database;
    @Getter
    private AltAccountManager altAccountManager;

    /**
     * Constructor of main class
     *
     * @param server         proxyserver
     * @param logger         logger class
     * @param dataDirectory  data path
     * @param metricsFactory bStats metrics
     */
    @Inject
    public Velocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory,
            Metrics.Factory metricsFactory) {
        instance = this;
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    /**
     * onEnable event of velocity
     *
     * @param event of startup
     */
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        commandManager = getServer().getCommandManager();
        setupFiles();

        this.shared = new Shared(
                UrlUtil.format(getConfigFile().getSettings().getUrl()),
                getConfigFile().getSettings().getApiKey(),
                new DebugVelocity());

        if (getConfigFile().getSettings().getUrl().equals("https://yourwebsite.com")) {
            getLogger()
                    .warn("You have not set the API URL in the config.yml file. Please set it to your LeaderOS URL.");
        } else if (getConfigFile().getSettings().getUrl().startsWith("http://")) {
            getLogger().warn(
                    "You are using an insecure URL (http://) for the API. Please use https:// for security reasons.");
        }

        CommandMeta commandMeta = Velocity.getInstance().getCommandManager().metaBuilder("leaderosauth")
                .plugin(Velocity.getInstance())
                .build();
        Velocity.getInstance().getCommandManager().register(commandMeta, new LeaderOSCommand());

        // bStats
        metricsFactory.make(this, 26806);

        this.factory = (LimboFactory) this.server.getPluginManager().getPlugin("limboapi")
                .flatMap(PluginContainer::getInstance).orElseThrow();

        createServer();

        this.server.getEventManager().register(this, new ConnectionListener(this));
        this.server.getEventManager().register(this, new IpConnectionLimitListener(this));

        // Initialize database for alt account tracking
        setupDatabase();
    }

    private void setupDatabase() {
        Config.Settings.Database dbConfig = configFile.getSettings().getDatabase();
        if (!dbConfig.isAltLoggerEnabled()) {
            logger.info("Alt account logger is disabled.");
            return;
        }

        String type = dbConfig.getType().toUpperCase();
        if (type.equals("MYSQL")) {
            this.database = new Mysql(logger, dbConfig.isDebug(), dbConfig.getPrefix(),
                    dbConfig.getMysqlHostname(), Integer.parseInt(dbConfig.getMysqlPort()),
                    dbConfig.getMysqlDatabase(), dbConfig.getMysqlUsername(),
                    dbConfig.getMysqlPassword(), dbConfig.getJdbcurlProperties());
        } else {
            this.database = new Sqlite(logger, dbConfig.isDebug(), dbConfig.getPrefix(),
                    dataDirectory.toFile());
        }

        if (!this.database.initialize()) {
            logger.error("Failed to initialize database for alt account tracking!");
            this.database = null;
            return;
        }

        this.altAccountManager = new AltAccountManager(this);
        logger.info("Alt account tracking initialized with " + type + " database.");

        // Auto-purge old records
        int expiration = dbConfig.getExpirationTime();
        if (expiration > 0) {
            int purged = this.database.purge(expiration);
            if (purged > 0) {
                logger.info("Purged " + purged + " old alt records (older than " + expiration + " days).");
            }
        }
    }

    /**
     * onDisable event of velocity
     *
     * @param event of disable
     */
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (database != null) {
            database.closeDataSource();
        }
    }

    /**
     * Setups config, lang and modules file file
     */
    public void setupFiles() {
        try {
            File configYml = new File(getDataDirectory().toFile().getAbsolutePath(), "config.yml");
            this.configFile = loadConfigWithRecovery(Config.class, configYml);
            this.configFile.save();

            String langName = configFile.getSettings().getLang();
            Class langClass = Class.forName("net.leaderos.auth.velocity.configuration.lang." + langName);
            @SuppressWarnings("unchecked")
            Class<Language> languageClass = langClass;
            File langYml = new File(getDataDirectory().toFile().getAbsolutePath() + "/lang", langName + ".yml");
            this.langFile = loadConfigWithRecovery(languageClass, langYml);
            this.langFile.save();
        } catch (Exception exception) {
            getLogger().error("Failed to load config/language files!", exception);
        }
    }

    private <T extends eu.okaeri.configs.OkaeriConfig> T loadConfigWithRecovery(Class<T> configClass, File file) {
        try {
            return ConfigManager.create(configClass, (it) -> {
                it.withConfigurer(new YamlSnakeYamlConfigurer());
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
                getLogger().warn("Config file " + file.getName() + " was corrupted! Backed up to " + broken.getName()
                        + " and recreated with defaults.");
            }
            return ConfigManager.create(configClass, (it) -> {
                it.withConfigurer(new YamlSnakeYamlConfigurer());
                it.withBindFile(file);
                it.withRemoveOrphans(true);
                it.saveDefaults();
                it.load(true);
            });
        }
    }

    private void createServer() {
        VirtualWorld world;
        long worldTime = 0;

        // Custom world
        if (configFile.getSettings().getCustomWorld().isEnabled()) {
            worldTime = configFile.getSettings().getCustomWorld().getWorldTicks();

            try {
                world = this.factory.createVirtualWorld(
                        Dimension.OVERWORLD,
                        configFile.getSettings().getCustomWorld().getSpawnLocation().getX(),
                        configFile.getSettings().getCustomWorld().getSpawnLocation().getY(),
                        configFile.getSettings().getCustomWorld().getSpawnLocation().getZ(),
                        (float) configFile.getSettings().getCustomWorld().getSpawnLocation().getYaw(),
                        (float) configFile.getSettings().getCustomWorld().getSpawnLocation().getPitch());
                Path path = this.dataDirectory.resolve(configFile.getSettings().getCustomWorld().getFile());
                WorldFile file = this.factory.openWorldFile(BuiltInWorldFileType.WORLDEDIT_SCHEM, path);
                file.toWorld(this.factory, world, 0, 0, 0, configFile.getSettings().getCustomWorld().getLightLevel());
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            // Empty World
            world = this.factory.createVirtualWorld(Dimension.THE_END, 0, 256, 0, 0, 0);
        }

        limboServer = factory.createLimbo(world)
                .setName("LeaderOS-Auth")
                .setWorldTime(worldTime)
                .setGameMode(GameMode.ADVENTURE);

        for (String command : this.configFile.getSettings().getLoginCommands()) {
            limboServer.registerCommand(new LimboCommandMeta(Collections.singleton(command)));
        }

        for (String command : this.configFile.getSettings().getRegisterCommands()) {
            limboServer.registerCommand(new LimboCommandMeta(Collections.singleton(command)));
        }

        for (String command : this.configFile.getSettings().getTfaCommands()) {
            limboServer.registerCommand(new LimboCommandMeta(Collections.singleton(command)));
        }
    }

    public void checkUpdate() {
        Velocity.getInstance().getServer().getScheduler().buildTask(Velocity.getInstance(), () -> {
            PluginUpdater updater = new PluginUpdater("1.0.5");
            try {
                if (updater.checkForUpdates()) {
                    Component msg = ChatUtil.replacePlaceholders(
                            Velocity.getInstance().getLangFile().getMessages().getUpdate(),
                            new Placeholder("%version%", updater.getLatestVersion()));
                    ChatUtil.sendMessage(getServer().getConsoleCommandSource(), msg);
                }
            } catch (Exception ignored) {
            }
        }).schedule();
    }
}