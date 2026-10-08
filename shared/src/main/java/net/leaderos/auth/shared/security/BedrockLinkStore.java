package net.leaderos.auth.shared.security;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Binds accounts to the Xbox identity (XUID) of a Bedrock player.
 *
 * <p>A binding is created only after the player proved the account password (and TFA when the
 * panel asked for it) while connected through Floodgate. Later logins of a verified Floodgate
 * player with that XUID may skip the password; the decision never relies on the name prefix.
 * An account keeps its first binding: another XUID that logs in with the password does not take
 * it over (that would let a leaked password plant a permanent passwordless login). A binding
 * expires when the account has not been verified with its password for the configured time.</p>
 */
public final class BedrockLinkStore {

    public enum BindResult {
        BOUND,
        REFRESHED,
        CONFLICT,
        ERROR
    }

    /** A stored binding. */
    public static final class Link {
        private final String xuid;
        private final long boundAt;
        private final long verifiedAt;

        public Link(String xuid, long boundAt, long verifiedAt) {
            this.xuid = xuid;
            this.boundAt = boundAt;
            this.verifiedAt = verifiedAt;
        }

        public String getXuid() {
            return xuid;
        }

        public long getBoundAt() {
            return boundAt;
        }

        public long getVerifiedAt() {
            return verifiedAt;
        }

        /**
         * @param xuid        XUID of the connecting Floodgate player
         * @param maxAgeMillis how long a password verification stays valid
         * @param now         current time in milliseconds
         * @return true when this binding vouches for that player now
         */
        public boolean trusts(String xuid, long maxAgeMillis, long now) {
            return isValidXuid(xuid) && this.xuid.equals(xuid) && maxAgeMillis > 0
                    && verifiedAt <= now + 60_000L && now - verifiedAt <= maxAgeMillis;
        }
    }

    private final DataSource dataSource;
    private final String table;
    private final RegistrationSecurityStore.Dialect dialect;
    private final RegistrationSecurityStore.ErrorSink errorSink;

    public BedrockLinkStore(DataSource dataSource, String prefix, RegistrationSecurityStore.Dialect dialect,
            RegistrationSecurityStore.ErrorSink errorSink) {
        if (dataSource == null) {
            throw new IllegalArgumentException("Data source cannot be null");
        }
        if (prefix == null || !prefix.matches("[A-Za-z0-9_]{1,32}")) {
            throw new IllegalArgumentException("Database prefix must match [A-Za-z0-9_]{1,32}");
        }
        this.dataSource = dataSource;
        this.table = prefix + "bedrock_links_v1";
        this.dialect = dialect;
        this.errorSink = errorSink;
    }

    public boolean initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "account_key VARCHAR(64) PRIMARY KEY NOT NULL, xuid VARCHAR(20) NOT NULL, "
                + "bound_at BIGINT NOT NULL, verified_at BIGINT NOT NULL)"
                + (dialect == RegistrationSecurityStore.Dialect.MYSQL
                        ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "");
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
            return true;
        } catch (SQLException exception) {
            report("Could not initialize the Bedrock link table", exception);
            return false;
        }
    }

    /**
     * Binds an account to an XUID after a password-verified login, or refreshes its verification time.
     *
     * @param account player name
     * @param xuid    XUID reported by Floodgate
     * @param now     current time in milliseconds
     * @return what happened
     */
    public BindResult bind(String account, String xuid, long now) {
        String key;
        try {
            key = accountKey(account);
        } catch (IllegalArgumentException invalid) {
            return BindResult.ERROR;
        }
        if (!isValidXuid(xuid)) {
            return BindResult.ERROR;
        }
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table
                    + " (account_key, xuid, bound_at, verified_at) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, key);
                insert.setString(2, xuid);
                insert.setLong(3, now);
                insert.setLong(4, now);
                insert.executeUpdate();
                return BindResult.BOUND;
            } catch (SQLException duplicate) {
                // The primary key decides races; read what is there now.
            }
            Link existing = find(connection, key);
            if (existing == null) {
                report("Could not store the Bedrock link of " + key, null);
                return BindResult.ERROR;
            }
            if (!existing.getXuid().equals(xuid)) {
                return BindResult.CONFLICT;
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE " + table
                    + " SET verified_at = ? WHERE account_key = ? AND xuid = ?")) {
                update.setLong(1, Math.max(now, existing.getVerifiedAt()));
                update.setString(2, key);
                update.setString(3, xuid);
                return update.executeUpdate() == 1 ? BindResult.REFRESHED : BindResult.ERROR;
            }
        } catch (SQLException exception) {
            report("Could not store the Bedrock link", exception);
            return BindResult.ERROR;
        }
    }

    /**
     * @param account player name
     * @return the binding of an account, or null when there is none or it could not be read
     */
    public Link find(String account) {
        String key;
        try {
            key = accountKey(account);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        try (Connection connection = dataSource.getConnection()) {
            return find(connection, key);
        } catch (SQLException exception) {
            report("Could not read the Bedrock link", exception);
            return null;
        }
    }

    /**
     * Removes the binding of an account.
     *
     * @return true when a binding was removed
     */
    public boolean unbind(String account) {
        String key;
        try {
            key = accountKey(account);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE account_key = ?")) {
            delete.setString(1, key);
            return delete.executeUpdate() > 0;
        } catch (SQLException exception) {
            report("Could not remove the Bedrock link", exception);
            return false;
        }
    }

    public static boolean isValidXuid(String xuid) {
        if (xuid == null || xuid.isEmpty() || xuid.length() > 20) {
            return false;
        }
        for (int i = 0; i < xuid.length(); i++) {
            char c = xuid.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return !"0".equals(xuid);
    }

    static String accountKey(String account) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }
        String value = account.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 64) {
            throw new IllegalArgumentException("Invalid account");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException("Invalid account");
            }
        }
        return value;
    }

    private Link find(Connection connection, String key) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT xuid, bound_at, verified_at FROM " + table + " WHERE account_key = ?")) {
            select.setString(1, key);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new Link(result.getString(1), result.getLong(2), result.getLong(3));
            }
        }
    }

    private void report(String message, Throwable throwable) {
        if (errorSink != null) {
            errorSink.error(message, throwable);
        }
    }
}
