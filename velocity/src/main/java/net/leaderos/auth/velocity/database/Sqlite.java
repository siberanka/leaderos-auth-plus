package net.leaderos.auth.velocity.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Sqlite extends Database {
    protected String initIpIndex;
    private final File dataFolder;

    public Sqlite(Logger logger, boolean debug, String prefix, File dataFolder) {
        super(logger, debug, prefix);
        this.dataFolder = dataFolder;

        initPlayer = "CREATE TABLE IF NOT EXISTS {prefix}playertable (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, uuid CHAR(36) UNIQUE NOT NULL, name VARCHAR(255) NOT NULL);";
        initIp = "CREATE TABLE IF NOT EXISTS {prefix}iptable (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ipaddr VARCHAR(255) NOT NULL, playerid INTEGER NOT NULL, date DATETIME NOT NULL, FOREIGN KEY (playerid) REFERENCES {prefix}playertable(id) ON DELETE CASCADE);";
        initRegistration = "CREATE TABLE IF NOT EXISTS {prefix}registrationtable (ipaddr VARCHAR(255) PRIMARY KEY NOT NULL, count INTEGER NOT NULL);";
        initIpIndex = "CREATE INDEX IF NOT EXISTS {prefix}iptable_ipaddr_index ON {prefix}iptable(ipaddr);";

        addIpEntry = "INSERT INTO {prefix}iptable (ipaddr, playerid, date) VALUES (?, (SELECT id FROM {prefix}playertable WHERE uuid = ?), datetime('now'));";
        updateIpEntry = "UPDATE {prefix}iptable SET date = datetime('now') WHERE ipaddr = ? AND playerid = (SELECT id FROM {prefix}playertable WHERE uuid = ?);";
        incrementReg = "INSERT INTO {prefix}registrationtable (ipaddr, count) VALUES (?, 1) ON CONFLICT(ipaddr) DO UPDATE SET count = count + 1;";
    }

    @Override
    public boolean initialize() {
        File dbFile = new File(dataFolder, "altdetector.db");

        if (!dbFile.exists()) {
            try {
                dbFile.getParentFile().mkdirs();
                dbFile.createNewFile();
            } catch (IOException e) {
                logger.warn("Unable to create SQLite database file: " + e.getMessage());
                return false;
            }
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.toString());
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setConnectionInitSql("PRAGMA foreign_keys = ON");
        dataSource = new HikariDataSource(hikariConfig);

        boolean success = executeStatement(replacePrefix(initPlayer));
        if (!success)
            return false;
        success = executeStatement(replacePrefix(initIp));
        if (!success)
            return false;
        success = executeStatement(replacePrefix(initRegistration));
        if (!success)
            return false;
        success = executeStatement(replacePrefix(initIpIndex));
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
        String query1 = "DELETE FROM {prefix}iptable WHERE date < date('now', ?);";
        String query2 = "DELETE FROM {prefix}playertable WHERE id NOT IN (SELECT playerid FROM {prefix}iptable);";

        Connection conn = getConnection();
        if (conn == null)
            return 0;
        try (Connection connection = conn;
                PreparedStatement statement1 = connection.prepareStatement(replacePrefix(query1));
                PreparedStatement statement2 = connection.prepareStatement(replacePrefix(query2))) {
            statement1.setString(1, "-" + expirationTime + " days");
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
