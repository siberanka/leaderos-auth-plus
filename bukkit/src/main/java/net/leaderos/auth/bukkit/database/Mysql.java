package net.leaderos.auth.bukkit.database;

import net.leaderos.auth.bukkit.Bukkit;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class Mysql extends Database {
    protected String initPlayer;
    protected String initIp;
    protected String initRegistration;

    public Mysql(Bukkit plugin, boolean debug, String prefix) {
        super(plugin, debug, prefix);
        initPlayer = "CREATE TABLE IF NOT EXISTS {prefix}playertable (id INTEGER PRIMARY KEY AUTO_INCREMENT NOT NULL, uuid CHAR(36) UNIQUE KEY NOT NULL, name VARCHAR(255) NOT NULL);";
        initIp = "CREATE TABLE IF NOT EXISTS {prefix}iptable (id INTEGER PRIMARY KEY AUTO_INCREMENT NOT NULL, ipaddr VARCHAR(255) NOT NULL, playerid INTEGER NOT NULL, date DATETIME NOT NULL, INDEX ipaddr_index (ipaddr), FOREIGN KEY (playerid) REFERENCES {prefix}playertable(id) ON DELETE CASCADE);";
        initRegistration = "CREATE TABLE IF NOT EXISTS {prefix}registrationtable (ipaddr VARCHAR(255) PRIMARY KEY NOT NULL, count INTEGER NOT NULL);";

        // MySQL uses now()
        addIpEntry = "INSERT INTO {prefix}iptable (ipaddr, playerid, date) VALUES (?, (SELECT id FROM {prefix}playertable WHERE uuid = ?), now());";
        updateIpEntry = "UPDATE {prefix}iptable SET date = now() WHERE ipaddr = ? AND playerid = (SELECT id FROM {prefix}playertable WHERE uuid = ?);";
        getAlts = "SELECT DISTINCT name FROM {prefix}iptable INNER JOIN {prefix}playertable ON {prefix}iptable.playerid = {prefix}playertable.id WHERE ipaddr IN (SELECT ipaddr FROM {prefix}iptable INNER JOIN {prefix}playertable ON {prefix}iptable.playerid = {prefix}playertable.id WHERE uuid = ?) AND uuid <> ? AND date >= SUBDATE(now(), ?) ORDER BY lower(name);";
        getAltsByIp = "SELECT DISTINCT name FROM {prefix}iptable INNER JOIN {prefix}playertable ON {prefix}iptable.playerid = {prefix}playertable.id WHERE ipaddr = ? AND date >= SUBDATE(now(), ?) ORDER BY lower(name);";
        getNetworkAltsByIp = "SELECT DISTINCT name FROM {prefix}iptable INNER JOIN {prefix}playertable ON {prefix}iptable.playerid = {prefix}playertable.id WHERE ipaddr IN (SELECT ipaddr FROM {prefix}iptable WHERE playerid IN (SELECT playerid FROM {prefix}iptable WHERE ipaddr = ? AND date >= SUBDATE(now(), ?))) AND date >= SUBDATE(now(), ?) ORDER BY lower(name);";

        // MySQL upsert
        incrementReg = "INSERT INTO {prefix}registrationtable (ipaddr, count) VALUES (?, 1) ON DUPLICATE KEY UPDATE count = count + 1;";
    }

    protected String formatExpirationTime(int expirationTime) {
        return Integer.toString(expirationTime);
    }

    public boolean initialize() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" +
                plugin.getConfigFile().getSettings().getDatabase().getMysqlHostname() + ":" +
                plugin.getConfigFile().getSettings().getDatabase().getMysqlPort() + "/" +
                plugin.getConfigFile().getSettings().getDatabase().getMysqlDatabase() +
                plugin.getConfigFile().getSettings().getDatabase().getJdbcurlProperties());
        hikariConfig.setUsername(plugin.getConfigFile().getSettings().getDatabase().getMysqlUsername());
        hikariConfig.setPassword(plugin.getConfigFile().getSettings().getDatabase().getMysqlPassword());
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "50");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "1024");

        if (debug) {
            plugin.getLogger().info("JDBC URL: " + hikariConfig.getJdbcUrl());
        }
        dataSource = new HikariDataSource(hikariConfig);

        // Send initial SQL statements
        boolean success = executeStatement(replacePrefix(initPlayer));
        if (!success)
            return false;

        success = executeStatement(replacePrefix(initIp));
        if (!success)
            return false;

        success = executeStatement(replacePrefix(initRegistration));
        if (!success)
            return false;

        return true;
    }

    @Override
    public boolean hasAccountLimitReached(String ip, int max) {
        String query = "SELECT COUNT(DISTINCT playerid) FROM {prefix}iptable WHERE ipaddr = ?";
        Connection conn = getConnection();
        if (conn == null)
            return false;
        try (Connection connection = conn;
                PreparedStatement statement = connection.prepareStatement(replacePrefix(query))) {
            statement.setString(1, ip);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) >= max;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public int purge(int expirationTime) {
        if (expirationTime <= 0)
            return 0;
        int entriesRemoved = 0;
        String query1 = "DELETE FROM {prefix}iptable WHERE date < SUBDATE(now(), ?);";
        String query2 = "DELETE FROM {prefix}playertable WHERE id NOT IN (SELECT playerid FROM {prefix}iptable);";

        Connection conn = getConnection();
        if (conn == null)
            return 0;
        try (Connection connection = conn;
                PreparedStatement statement1 = connection.prepareStatement(replacePrefix(query1));
                PreparedStatement statement2 = connection.prepareStatement(replacePrefix(query2))) {

            statement1.setInt(1, expirationTime);
            entriesRemoved = statement1.executeUpdate();

            statement2.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return entriesRemoved;
    }

    @Override
    public List<String> getAltsByName(String playerName) {
        String query = "SELECT DISTINCT name FROM {prefix}iptable INNER JOIN {prefix}playertable ON {prefix}iptable.playerid = {prefix}playertable.id WHERE ipaddr IN (SELECT ipaddr FROM {prefix}iptable INNER JOIN {prefix}playertable ON {prefix}iptable.playerid = {prefix}playertable.id WHERE name = ?) AND name <> ? ORDER BY lower(name);";
        List<String> alts = new ArrayList<>();
        Connection conn = getConnection();
        if (conn == null)
            return alts;
        try (Connection connection = conn;
                PreparedStatement statement = connection.prepareStatement(replacePrefix(query))) {
            statement.setString(1, playerName);
            statement.setString(2, playerName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    alts.add(resultSet.getString("name"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return alts;
    }

    @Override
    public List<String> getAltsByIp(String ip, int expirationTime) {
        List<String> alts = new ArrayList<>();
        Connection conn = getConnection();
        if (conn == null)
            return alts;
        try (Connection connection = conn;
                PreparedStatement statement = connection.prepareStatement(replacePrefix(getAltsByIp))) {
            statement.setString(1, ip);
            statement.setInt(2, expirationTime);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    alts.add(resultSet.getString("name"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return alts;
    }

    @Override
    public int deletePlayerAlts(String playerName) {
        String query = "DELETE FROM {prefix}iptable WHERE playerid = (SELECT id FROM {prefix}playertable WHERE name = ?);";
        int deleted = 0;
        Connection conn = getConnection();
        if (conn == null)
            return 0;
        try (Connection connection = conn;
                PreparedStatement statement = connection.prepareStatement(replacePrefix(query))) {
            statement.setString(1, playerName);
            deleted = statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return deleted;
    }
}
