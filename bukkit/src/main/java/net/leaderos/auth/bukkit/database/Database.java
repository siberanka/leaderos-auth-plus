package net.leaderos.auth.bukkit.database;

import com.zaxxer.hikari.HikariDataSource;
import net.leaderos.auth.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class Database {
    protected final Bukkit plugin;
    protected HikariDataSource dataSource;
    protected String prefix;
    protected boolean debug;

    // Queries to be set by implementations
    protected String initPlayer;
    protected String initIp;
    protected String initRegistration;

    protected String addPlayerEntry = "INSERT INTO {prefix}playertable (uuid, name) VALUES (?, ?);";
    protected String updatePlayerEntry = "UPDATE {prefix}playertable SET name = ? WHERE uuid = ?;";
    protected String checkPlayerEntry = "SELECT id FROM {prefix}playertable WHERE uuid = ?;";

    protected String addIpEntry; // Defined by impl since datetime function varies
    protected String updateIpEntry; // Defined by impl
    protected String checkIpEntry = "SELECT EXISTS (SELECT 1 FROM {prefix}iptable INNER JOIN {prefix}playertable ON {prefix}iptable.playerid = {prefix}playertable.id WHERE ipaddr = ? AND uuid = ?);";

    protected String getAlts = "SELECT DISTINCT name FROM {prefix}iptable INNER JOIN {prefix}playertable ON {prefix}iptable.playerid = {prefix}playertable.id WHERE ipaddr IN (SELECT ipaddr FROM {prefix}iptable INNER JOIN {prefix}playertable ON {prefix}iptable.playerid = {prefix}playertable.id WHERE uuid = ?) AND uuid <> ? ORDER BY lower(name);";

    protected String incrementReg; // Defined by impl since upsert syntax varies
    protected String checkReg = "SELECT count FROM {prefix}registrationtable WHERE ipaddr = ?;";

    public Database(Bukkit plugin, boolean debug, String prefix) {
        this.plugin = plugin;
        this.debug = debug;
        this.prefix = prefix;
    }

    public abstract boolean initialize();

    /**
     * Checks if a player's IP limit has been reached.
     * 
     * @param ip  IP Address
     * @param max Max accounts per IP
     * @return true if max accounts reached
     */
    public abstract boolean hasAccountLimitReached(String ip, int max);

    /**
     * Purges old IP/Player records from the database.
     * 
     * @param expirationTime In days
     * @return Number of records deleted.
     */
    public abstract int purge(int expirationTime);

    /**
     * Retrieves alt accounts by player name (for placeholders/commands).
     * 
     * @param playerName Name of the parameter to fetch alts for
     * @return Array list of names
     */
    public abstract List<String> getAltsByName(String playerName);

    /**
     * Deletes IP history for a given player name.
     * 
     * @param playerName Name of the parameter to delete alts for
     * @return Number of records deleted.
     */
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
            plugin.getLogger().warning("Error getting database connection: " + e.getMessage());
        }
        return null;
    }

    protected boolean executeStatement(String statement) {
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(statement)) {
            if (debug)
                plugin.getLogger().info("Executing statement: " + statement);
            stmt.execute();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Database error executing statement: " + statement + ": " + e.getMessage());
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
            plugin.getLogger().warning("Error adding/updating player: " + e.getMessage());
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
            plugin.getLogger().warning("Error adding/updating ip: " + e.getMessage());
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
            plugin.getLogger().warning("Error retrieving alts: " + e.getMessage());
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
            plugin.getLogger().warning("Error incrementing registration: " + e.getMessage());
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
            plugin.getLogger().warning("Error checking registration limit: " + e.getMessage());
        }
        return false;
    }
}
