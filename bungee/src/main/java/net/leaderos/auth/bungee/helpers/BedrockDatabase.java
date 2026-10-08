package net.leaderos.auth.bungee.helpers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.leaderos.auth.bungee.Bungee;
import net.leaderos.auth.bungee.configuration.Config;
import net.leaderos.auth.shared.security.BedrockLinkStore;
import net.leaderos.auth.shared.security.RegistrationSecurityStore;

/**
 * Read access to the Bedrock (XUID) bindings the auth server stores in the shared MySQL database.
 */
public class BedrockDatabase {

    private final Bungee plugin;
    private HikariDataSource dataSource;
    private BedrockLinkStore store;

    public BedrockDatabase(Bungee plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        Config.Settings.Bedrock.Database config = plugin.getConfigFile().getSettings().getBedrock().getDatabase();
        try {
            int port = Integer.parseInt(config.getMysqlPort());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("out of range");
            }
            HikariConfig hikari = new HikariConfig();
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setJdbcUrl("jdbc:mysql://" + config.getMysqlHostname() + ":" + port + "/"
                    + config.getMysqlDatabase() + config.getJdbcurlProperties());
            hikari.setUsername(config.getMysqlUsername());
            hikari.setPassword(config.getMysqlPassword());
            hikari.setMaximumPoolSize(4);
            hikari.setConnectionTimeout(3000);
            hikari.setPoolName("LeaderOS-Auth-Bedrock");
            dataSource = new HikariDataSource(hikari);
            store = new BedrockLinkStore(dataSource, config.getPrefix(), RegistrationSecurityStore.Dialect.MYSQL,
                    (message, throwable) -> plugin.getLogger().severe(message
                            + (throwable != null ? ": " + throwable.getMessage() : "")));
            return store.initialize();
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Could not connect to the Bedrock trust database: " + exception.getMessage());
            store = null;
            return false;
        }
    }

    public BedrockLinkStore getStore() {
        return store;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
