package net.leaderos.auth.bungee;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bungee.YamlBungeeConfigurer;
import lombok.Getter;
import net.leaderos.auth.bungee.configuration.Config;
import net.leaderos.auth.bungee.helpers.DebugBungee;
import net.leaderos.auth.bungee.listener.IpConnectionLimitListener;
import net.leaderos.auth.bungee.listener.PlayerListener;
import net.leaderos.auth.bungee.listener.PluginMessageListener;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.helpers.Placeholder;
import net.leaderos.auth.shared.helpers.PluginUpdater;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import org.bstats.bungeecord.Metrics;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

@Getter
public class Bungee extends Plugin {

    @Getter
    private static Bungee instance;
    private final Map<String, Boolean> authenticatedPlayers = new HashMap<>();
    private Shared shared;
    private Config configFile;

    @Override
    public void onEnable() {
        instance = this;

        setupFiles();

        shared = new Shared("", "", new DebugBungee());

        new Metrics(this, 26805);

        this.getProxy().getPluginManager().registerListener(this, new PluginMessageListener(this));
        this.getProxy().getPluginManager().registerListener(this, new PlayerListener(this));
        this.getProxy().getPluginManager().registerListener(this, new IpConnectionLimitListener(this));

        String authServerName = configFile.getSettings().getAuthServer();
        ServerInfo serverInfo = getProxy().getServerInfo(authServerName);
        if (serverInfo == null) {
            getLogger().severe("Auth server '" + authServerName + "' not found. Please check your config.yml.");
        }
    }

    public void setupFiles() {
        try {
            File configYml = new File(this.getDataFolder().getAbsolutePath(), "config.yml");
            this.configFile = loadConfigWithRecovery(configYml);
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
