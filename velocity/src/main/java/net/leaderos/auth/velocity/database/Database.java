package net.leaderos.auth.velocity.database;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class Database {
    protected final Logger logger;
    protected HikariDataSource dataSource;
    protected String prefix;
    protected boolean debug;

    protected String initPlayer;
    protected String initIp;
    protected String initRegistration;

    protected String addPlayerEntry = "INSERT INTO {prefix}playertable (uuid, name) VALUES (?, ?);";
    protected String updatePlayerEntry = "UPDATE {prefix}playertable SET name = ? WHERE uuid = ?;";
    protected String checkPlayerEntry = "SELECT id FROM {prefix}playertable WHERE uuid = ?;";

    protected String addIpEntry;
    protected String updateIpEntry;
    protected String checkIpEntry = "SELECT EXISTS (SELECT 1 FROM {prefix}iptable INNER JOIN {prefix}playertable ON {prefix}iptable.playerid = {prefix}playertable.id WHERE ipaddr = ? AND uuid = ?);";

    protected String getAlts = "SELECT DISTINCT name FROM {prefix}iptable INNER JOIN {prefix}playertable ON {prefix}iptable.playerid = {prefix}playertable.id WHERE ipaddr IN (SELECT ipaddr FROM {prefix}iptable INNER JOIN {prefix}playertable ON {prefix}iptable.playerid = {prefix}playertable.id WHERE uuid = ?) AND uuid <> ? ORDER BY lower(name);";

    protected String incrementReg;
    protected String checkReg = "SELECT count FROM {prefix}registrationtable WHERE ipaddr = ?;";

    public Database(Logger logger, boolean debug, String prefix) {
        this.logger = logger;
        this.debug = debug;
        this.prefix = prefix;
    }

    public abstract boolean initialize();

    public abstract boolean hasAccountLimitReached(String ip, int max);

    public abstract int purge(int expirationTime);

    public abstract List<String> getAltsByName(String playerName);

    public abstract int deletePlayerAlts(String playerName);

    protected String replacePrefix(String statement) {
        return statement.replace("{prefix}", prefix);
    }

    public void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    protected Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            logger.warn("Error getting database connection: " + e.getMessage());
        }
        return null;
    }

    protected boolean executeStatement(String statement) {
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(statement)) {
            if (debug)
                logger.info("Executing statement: " + statement);
            stmt.execute();
            return true;
        } catch (SQLException e) {
            logger.warn("Database error executing statement: " + statement + ": " + e.getMessage());
        }
        return false;
    }

    public void addOrUpdatePlayer(String uuid, String name) {
        try (Connection conn = getConnection()) {
            if (conn == null)
                return;
            boolean exists = false;
            try (PreparedStatement stmt = conn.prepareStatement(replacePrefix(checkPlayerEntry))) {
                stmt.setString(1, uuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next())
                        exists = true;
                }
            }
            if (!exists) {
                try (PreparedStatement stmt = conn.prepareStatement(replacePrefix(addPlayerEntry))) {
                    stmt.setString(1, uuid);
                    stmt.setString(2, name);
                    stmt.executeUpdate();
                }
            } else {
                try (PreparedStatement stmt = conn.prepareStatement(replacePrefix(updatePlayerEntry))) {
                    stmt.setString(1, name);
                    stmt.setString(2, uuid);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            logger.warn("Error adding/updating player: " + e.getMessage());
        }
    }

    public void addOrUpdateIp(String ip, String uuid) {
        try (Connection conn = getConnection()) {
            if (conn == null)
                return;
            boolean exists = false;
            try (PreparedStatement stmt = conn.prepareStatement(replacePrefix(checkIpEntry))) {
                stmt.setString(1, ip);
                stmt.setString(2, uuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next())
                        exists = rs.getBoolean(1);
                }
            }
            if (!exists) {
                try (PreparedStatement stmt = conn.prepareStatement(replacePrefix(addIpEntry))) {
                    stmt.setString(1, ip);
                    stmt.setString(2, uuid);
                    stmt.executeUpdate();
                }
            } else {
                try (PreparedStatement stmt = conn.prepareStatement(replacePrefix(updateIpEntry))) {
                    stmt.setString(1, ip);
                    stmt.setString(2, uuid);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            logger.warn("Error adding/updating ip: " + e.getMessage());
        }
    }

    public List<String> getAltNames(String uuid) {
        List<String> alts = new ArrayList<>();
        try (Connection conn = getConnection()) {
            if (conn == null)
                return alts;
            try (PreparedStatement stmt = conn.prepareStatement(replacePrefix(getAlts))) {
                stmt.setString(1, uuid);
                stmt.setString(2, uuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        alts.add(rs.getString("name"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error retrieving alts: " + e.getMessage());
        }
        return alts;
    }

    public void incrementRegistration(String ip) {
        try (Connection conn = getConnection()) {
            if (conn == null)
                return;
            try (PreparedStatement stmt = conn.prepareStatement(replacePrefix(incrementReg))) {
                stmt.setString(1, ip);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warn("Error incrementing registration: " + e.getMessage());
        }
    }

    public boolean hasReachedRegistrationLimit(String ip, int max) {
        try (Connection conn = getConnection()) {
            if (conn == null)
                return false;
            try (PreparedStatement stmt = conn.prepareStatement(replacePrefix(checkReg))) {
                stmt.setString(1, ip);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("count") >= max;
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error checking registration limit: " + e.getMessage());
        }
        return false;
    }
}
