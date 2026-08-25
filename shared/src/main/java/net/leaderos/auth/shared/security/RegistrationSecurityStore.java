package net.leaderos.auth.shared.security;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Durable and transactionally serialized registration limiter shared by all
 * platforms. Registration attempts are reserved before the remote API call,
 * closing the classic check-then-increment race.
 */
public final class RegistrationSecurityStore {

    public enum Dialect {
        SQLITE,
        MYSQL
    }

    public interface ErrorSink {
        void error(String message, Throwable throwable);
    }

    private static final int MAX_GRAPH_NODES = 100_000;
    private static final String MIGRATION_KEY = "legacy-import-v1";

    private final DataSource dataSource;
    private final String prefix;
    private final Dialect dialect;
    private final ErrorSink errorSink;
    private final int ipv6PrefixLength;

    public RegistrationSecurityStore(DataSource dataSource, String prefix, Dialect dialect,
            int ipv6PrefixLength, ErrorSink errorSink) {
        if (dataSource == null) {
            throw new IllegalArgumentException("Data source cannot be null");
        }
        if (prefix == null || !prefix.matches("[A-Za-z0-9_]{1,32}")) {
            throw new IllegalArgumentException("Database prefix must match [A-Za-z0-9_]{1,32}");
        }
        this.dataSource = dataSource;
        this.prefix = prefix;
        this.dialect = dialect;
        this.errorSink = errorSink;
        this.ipv6PrefixLength = IpAddressNormalizer.clampIpv6Prefix(ipv6PrefixLength);
    }

    public boolean initialize() {
        try (Connection connection = dataSource.getConnection()) {
            createSchema(connection);
            return migrateLegacyData(connection);
        } catch (SQLException exception) {
            report("Could not initialize registration security schema", exception);
            return false;
        }
    }

    public RegistrationDecision reserve(String rawIp, String playerName, int maximumAccounts,
            int reservationTimeoutSeconds) {
        if (maximumAccounts < 1) {
            return RegistrationDecision.denied(RegistrationDecision.Status.SECURITY_ERROR, 0,
                    Collections.emptyList());
        }

        final String ip;
        final String accountKey;
        try {
            ip = IpAddressNormalizer.normalize(rawIp, ipv6PrefixLength);
            accountKey = normalizeAccount(playerName);
        } catch (IllegalArgumentException exception) {
            report("Rejected registration because its identity could not be normalized", exception);
            return RegistrationDecision.denied(RegistrationDecision.Status.SECURITY_ERROR, 0,
                    Collections.emptyList());
        }

        try (Connection connection = dataSource.getConnection()) {
            beginLockedTransaction(connection);
            try {
                long now = System.currentTimeMillis();
                deleteExpiredReservations(connection, now);

                if (hasPendingReservation(connection, accountKey, now)) {
                    rollback(connection);
                    return RegistrationDecision.denied(RegistrationDecision.Status.ALREADY_PENDING, 0,
                            Collections.emptyList());
                }

                Network network = loadNetwork(connection, ip, accountKey, now);
                boolean alreadyKnown = network.registeredAccounts.contains(accountKey);
                int projectedCount = network.allAccounts.size() + (alreadyKnown ? 0 : 1);
                List<String> names = getDisplayNames(connection, network.registeredAccounts);
                if (projectedCount > maximumAccounts) {
                    rollback(connection);
                    return RegistrationDecision.denied(RegistrationDecision.Status.LIMIT_REACHED,
                            network.allAccounts.size(), names);
                }

                String token = UUID.randomUUID().toString();
                long timeoutMillis = Math.max(120, Math.min(3600, reservationTimeoutSeconds)) * 1000L;
                try (PreparedStatement statement = connection.prepareStatement(sql(
                        "INSERT INTO {prefix}registration_reservations_v2 "
                                + "(token, ipaddr, account_key, display_name, created_at, expires_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?)"))) {
                    statement.setString(1, token);
                    statement.setString(2, ip);
                    statement.setString(3, accountKey);
                    statement.setString(4, safeDisplayName(playerName));
                    statement.setLong(5, now);
                    statement.setLong(6, now + timeoutMillis);
                    statement.executeUpdate();
                }
                connection.commit();
                return RegistrationDecision.allowed(token, network.allAccounts.size(), names);
            } catch (SQLException | RuntimeException exception) {
                rollback(connection);
                throw exception;
            }
        } catch (SQLException | RuntimeException exception) {
            report("Registration security reservation failed; registration was denied (fail-closed)", exception);
            return RegistrationDecision.denied(RegistrationDecision.Status.SECURITY_ERROR, 0,
                    Collections.emptyList());
        }
    }

    public boolean commit(String token, String uuid, String playerName, String rawIp) {
        if (token == null || token.isEmpty()) {
            report("Refused to commit a registration without a reservation token", null);
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            beginLockedTransaction(connection);
            try {
                Reservation reservation = findReservation(connection, token);
                if (reservation == null) {
                    rollback(connection);
                    report("Registration reservation expired before it could be committed: " + token, null);
                    return false;
                }
                String suppliedAccount = normalizeAccount(playerName);
                String suppliedIp = IpAddressNormalizer.normalize(rawIp, ipv6PrefixLength);
                if (!reservation.accountKey.equals(suppliedAccount) || !reservation.ip.equals(suppliedIp)) {
                    rollback(connection);
                    report("Registration reservation identity mismatch", null);
                    return false;
                }

                upsertAccount(connection, reservation.accountKey, uuid, safeDisplayName(playerName),
                        System.currentTimeMillis());
                upsertLink(connection, reservation.ip, reservation.accountKey, System.currentTimeMillis());
                deleteReservation(connection, token);
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection);
                throw exception;
            }
        } catch (SQLException | RuntimeException exception) {
            report("Could not commit registration security record", exception);
            return false;
        }
    }

    public void release(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql(
                        "DELETE FROM {prefix}registration_reservations_v2 WHERE token = ?"))) {
            statement.setString(1, token);
            statement.executeUpdate();
        } catch (SQLException exception) {
            report("Could not release registration reservation " + token, exception);
        }
    }

    /** Records every successfully authenticated identity, independently of alt notifications. */
    public boolean recordAuthenticatedAccount(String uuid, String playerName, String rawIp) {
        try {
            String accountKey = normalizeAccount(playerName);
            String ip = IpAddressNormalizer.normalize(rawIp, ipv6PrefixLength);
            try (Connection connection = dataSource.getConnection()) {
                beginLockedTransaction(connection);
                try {
                    long now = System.currentTimeMillis();
                    upsertAccount(connection, accountKey, uuid, safeDisplayName(playerName), now);
                    upsertLink(connection, ip, accountKey, now);
                    connection.commit();
                    return true;
                } catch (SQLException | RuntimeException exception) {
                    rollback(connection);
                    throw exception;
                }
            }
        } catch (SQLException | RuntimeException exception) {
            report("Could not record authenticated account for registration security", exception);
            return false;
        }
    }

    public List<String> getNetworkAccountNames(String rawIp, String excludedPlayerName) {
        try (Connection connection = dataSource.getConnection()) {
            String ip = IpAddressNormalizer.normalize(rawIp, ipv6PrefixLength);
            String excluded = excludedPlayerName == null ? null : normalizeAccount(excludedPlayerName);
            Network network = loadNetwork(connection, ip, excluded, System.currentTimeMillis());
            Set<String> accounts = new LinkedHashSet<>(network.registeredAccounts);
            if (excluded != null) {
                accounts.remove(excluded);
            }
            return getDisplayNames(connection, accounts);
        } catch (SQLException | RuntimeException exception) {
            report("Could not retrieve linked account network", exception);
            return Collections.emptyList();
        }
    }

    private void createSchema(Connection connection) throws SQLException {
        execute(connection, withMysqlEngine("CREATE TABLE IF NOT EXISTS {prefix}registration_accounts_v2 ("
                + "account_key VARCHAR(64) PRIMARY KEY NOT NULL, uuid VARCHAR(36), "
                + "display_name VARCHAR(32) NOT NULL, registered_at BIGINT NOT NULL)"));

        String links = "CREATE TABLE IF NOT EXISTS {prefix}registration_links_v2 ("
                + "ipaddr VARCHAR(64) NOT NULL, account_key VARCHAR(64) NOT NULL, last_seen BIGINT NOT NULL, "
                + "PRIMARY KEY (ipaddr, account_key)"
                + (dialect == Dialect.MYSQL ? ", INDEX registration_links_account_idx (account_key)" : "")
                + ")";
        execute(connection, withMysqlEngine(links));

        String reservations = "CREATE TABLE IF NOT EXISTS {prefix}registration_reservations_v2 ("
                + "token VARCHAR(36) PRIMARY KEY NOT NULL, ipaddr VARCHAR(64) NOT NULL, "
                + "account_key VARCHAR(64) NOT NULL UNIQUE, display_name VARCHAR(32) NOT NULL, "
                + "created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL"
                + (dialect == Dialect.MYSQL ? ", INDEX registration_reservations_ip_idx (ipaddr)" : "")
                + ")";
        execute(connection, withMysqlEngine(reservations));

        execute(connection, withMysqlEngine("CREATE TABLE IF NOT EXISTS {prefix}registration_lock_v2 ("
                + "id INTEGER PRIMARY KEY NOT NULL, version BIGINT NOT NULL)"));
        execute(connection, withMysqlEngine("CREATE TABLE IF NOT EXISTS {prefix}registration_meta_v2 ("
                + "meta_key VARCHAR(64) PRIMARY KEY NOT NULL, meta_value VARCHAR(255) NOT NULL)"));

        if (dialect == Dialect.SQLITE) {
            execute(connection, "CREATE INDEX IF NOT EXISTS {prefix}registration_links_account_idx "
                    + "ON {prefix}registration_links_v2(account_key)");
            execute(connection, "CREATE INDEX IF NOT EXISTS {prefix}registration_reservations_ip_idx "
                    + "ON {prefix}registration_reservations_v2(ipaddr)");
            execute(connection, "INSERT OR IGNORE INTO {prefix}registration_lock_v2 (id, version) VALUES (1, 0)");
        } else {
            execute(connection, "INSERT IGNORE INTO {prefix}registration_lock_v2 (id, version) VALUES (1, 0)");
        }
    }

    private boolean migrateLegacyData(Connection connection) {
        try {
            beginLockedTransaction(connection);
            if (metadataExists(connection, MIGRATION_KEY)) {
                connection.commit();
                return true;
            }

            // Import the complete historical account/IP graph first.
            try (PreparedStatement statement = connection.prepareStatement(sql(
                    "SELECT p.uuid, p.name, i.ipaddr FROM {prefix}iptable i "
                            + "INNER JOIN {prefix}playertable p ON i.playerid = p.id"));
                    ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String account = normalizeAccount(result.getString("name"));
                    String ip = IpAddressNormalizer.normalize(result.getString("ipaddr"), ipv6PrefixLength);
                    long now = System.currentTimeMillis();
                    upsertAccount(connection, account, result.getString("uuid"),
                            safeDisplayName(result.getString("name")), now);
                    upsertLink(connection, ip, account, now);
                }
            }

            // Preserve counters from older versions with conservative anonymous slots.
            try (PreparedStatement statement = connection.prepareStatement(sql(
                    "SELECT ipaddr, count FROM {prefix}registrationtable"));
                    ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String ip = IpAddressNormalizer.normalize(result.getString("ipaddr"), ipv6PrefixLength);
                    int legacyCount = Math.max(0, result.getInt("count"));
                    int knownCount = countDirectRegisteredAccounts(connection, ip);
                    for (int i = knownCount; i < legacyCount; i++) {
                        String key = "legacy:" + shortHash(ip) + ":" + i;
                        upsertAccount(connection, key, null, "legacy-account-" + (i + 1),
                                System.currentTimeMillis());
                        upsertLink(connection, ip, key, System.currentTimeMillis());
                    }
                }
            }

            insertMetadata(connection, MIGRATION_KEY, Long.toString(System.currentTimeMillis()));
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException exception) {
            rollback(connection);
            report("Could not migrate legacy registration security data", exception);
            return false;
        }
    }

    private void beginLockedTransaction(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "UPDATE {prefix}registration_lock_v2 SET version = version + 1 WHERE id = 1"))) {
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Registration lock row is missing");
            }
        }
    }

    private Network loadNetwork(Connection connection, String initialIp, String initialAccount, long now)
            throws SQLException {
        Set<String> ips = new LinkedHashSet<>();
        Set<String> accounts = new LinkedHashSet<>();
        Set<String> registered = new LinkedHashSet<>();
        Queue<Node> queue = new ArrayDeque<>();
        if (initialIp != null) {
            ips.add(initialIp);
            queue.add(Node.ip(initialIp));
        }
        if (initialAccount != null && accountExists(connection, initialAccount)) {
            accounts.add(initialAccount);
            registered.add(initialAccount);
            queue.add(Node.account(initialAccount));
        }

        while (!queue.isEmpty()) {
            if (ips.size() + accounts.size() > MAX_GRAPH_NODES) {
                throw new SQLException("Registration identity graph exceeded safety limit");
            }
            Node node = queue.remove();
            if (node.ip) {
                loadAccountsForIp(connection, node.value, now, accounts, registered, queue);
            } else {
                loadIpsForAccount(connection, node.value, now, ips, queue);
            }
        }
        return new Network(accounts, registered);
    }

    private void loadAccountsForIp(Connection connection, String ip, long now, Set<String> accounts,
            Set<String> registered, Queue<Node> queue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "SELECT account_key FROM {prefix}registration_links_v2 WHERE ipaddr = ?"))) {
            statement.setString(1, ip);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String account = result.getString(1);
                    registered.add(account);
                    if (accounts.add(account)) {
                        queue.add(Node.account(account));
                    }
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "SELECT account_key FROM {prefix}registration_reservations_v2 "
                        + "WHERE ipaddr = ? AND expires_at >= ?"))) {
            statement.setString(1, ip);
            statement.setLong(2, now);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String account = result.getString(1);
                    if (accounts.add(account)) {
                        queue.add(Node.account(account));
                    }
                }
            }
        }
    }

    private void loadIpsForAccount(Connection connection, String account, long now, Set<String> ips,
            Queue<Node> queue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "SELECT ipaddr FROM {prefix}registration_links_v2 WHERE account_key = ?"))) {
            statement.setString(1, account);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String ip = result.getString(1);
                    if (ips.add(ip)) {
                        queue.add(Node.ip(ip));
                    }
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "SELECT ipaddr FROM {prefix}registration_reservations_v2 "
                        + "WHERE account_key = ? AND expires_at >= ?"))) {
            statement.setString(1, account);
            statement.setLong(2, now);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String ip = result.getString(1);
                    if (ips.add(ip)) {
                        queue.add(Node.ip(ip));
                    }
                }
            }
        }
    }

    private void upsertAccount(Connection connection, String account, String uuid, String displayName, long now)
            throws SQLException {
        String statementSql = dialect == Dialect.SQLITE
                ? "INSERT INTO {prefix}registration_accounts_v2 "
                        + "(account_key, uuid, display_name, registered_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(account_key) DO UPDATE SET uuid = COALESCE(excluded.uuid, uuid), "
                        + "display_name = excluded.display_name"
                : "INSERT INTO {prefix}registration_accounts_v2 "
                        + "(account_key, uuid, display_name, registered_at) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE uuid = COALESCE(VALUES(uuid), uuid), "
                        + "display_name = VALUES(display_name)";
        try (PreparedStatement statement = connection.prepareStatement(sql(statementSql))) {
            statement.setString(1, account);
            statement.setString(2, uuid);
            statement.setString(3, displayName);
            statement.setLong(4, now);
            statement.executeUpdate();
        }
    }

    private void upsertLink(Connection connection, String ip, String account, long now) throws SQLException {
        String statementSql = dialect == Dialect.SQLITE
                ? "INSERT INTO {prefix}registration_links_v2 (ipaddr, account_key, last_seen) VALUES (?, ?, ?) "
                        + "ON CONFLICT(ipaddr, account_key) DO UPDATE SET last_seen = excluded.last_seen"
                : "INSERT INTO {prefix}registration_links_v2 (ipaddr, account_key, last_seen) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE last_seen = VALUES(last_seen)";
        try (PreparedStatement statement = connection.prepareStatement(sql(statementSql))) {
            statement.setString(1, ip);
            statement.setString(2, account);
            statement.setLong(3, now);
            statement.executeUpdate();
        }
    }

    private List<String> getDisplayNames(Connection connection, Set<String> accounts) throws SQLException {
        List<String> names = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "SELECT display_name FROM {prefix}registration_accounts_v2 WHERE account_key = ?"))) {
            for (String account : accounts) {
                statement.setString(1, account);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        names.add(result.getString(1));
                    }
                }
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private boolean accountExists(Connection connection, String account) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "SELECT 1 FROM {prefix}registration_accounts_v2 WHERE account_key = ?"))) {
            statement.setString(1, account);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean hasPendingReservation(Connection connection, String account, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "SELECT 1 FROM {prefix}registration_reservations_v2 "
                        + "WHERE account_key = ? AND expires_at >= ?"))) {
            statement.setString(1, account);
            statement.setLong(2, now);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private Reservation findReservation(Connection connection, String token) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "SELECT ipaddr, account_key FROM {prefix}registration_reservations_v2 WHERE token = ?"))) {
            statement.setString(1, token);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new Reservation(result.getString(1), result.getString(2)) : null;
            }
        }
    }

    private void deleteExpiredReservations(Connection connection, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "DELETE FROM {prefix}registration_reservations_v2 WHERE expires_at < ?"))) {
            statement.setLong(1, now);
            statement.executeUpdate();
        }
    }

    private void deleteReservation(Connection connection, String token) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "DELETE FROM {prefix}registration_reservations_v2 WHERE token = ?"))) {
            statement.setString(1, token);
            statement.executeUpdate();
        }
    }

    private int countDirectRegisteredAccounts(Connection connection, String ip) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "SELECT COUNT(*) FROM {prefix}registration_links_v2 WHERE ipaddr = ?"))) {
            statement.setString(1, ip);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private boolean metadataExists(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "SELECT 1 FROM {prefix}registration_meta_v2 WHERE meta_key = ?"))) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void insertMetadata(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql(
                "INSERT INTO {prefix}registration_meta_v2 (meta_key, meta_value) VALUES (?, ?)"))) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private void execute(Connection connection, String rawSql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql(rawSql));
        }
    }

    private String sql(String rawSql) {
        return rawSql.replace("{prefix}", prefix);
    }

    private String withMysqlEngine(String statement) {
        return dialect == Dialect.MYSQL ? statement + " ENGINE=InnoDB" : statement;
    }

    private static String normalizeAccount(String playerName) {
        if (playerName == null) {
            throw new IllegalArgumentException("Player name cannot be null");
        }
        String value = playerName.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 64 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid player name");
        }
        return fullHash(value);
    }

    private static String safeDisplayName(String playerName) {
        String value = playerName == null ? "unknown" : playerName.trim().replaceAll("[\\p{Cntrl}]", "?");
        return value.length() > 32 ? value.substring(0, 32) : value;
    }

    private static String shortHash(String value) {
        return fullHash(value).substring(0, 24);
    }

    private static String fullHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (byte item : digest) {
                output.append(String.format("%02x", item));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void report(String message, Throwable throwable) {
        if (errorSink != null) {
            errorSink.error(message, throwable);
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static final class Node {
        private final boolean ip;
        private final String value;

        private Node(boolean ip, String value) {
            this.ip = ip;
            this.value = value;
        }

        private static Node ip(String value) {
            return new Node(true, value);
        }

        private static Node account(String value) {
            return new Node(false, value);
        }
    }

    private static final class Network {
        private final Set<String> allAccounts;
        private final Set<String> registeredAccounts;

        private Network(Set<String> allAccounts, Set<String> registeredAccounts) {
            this.allAccounts = new HashSet<>(allAccounts);
            this.registeredAccounts = new LinkedHashSet<>(registeredAccounts);
        }
    }

    private static final class Reservation {
        private final String ip;
        private final String accountKey;

        private Reservation(String ip, String accountKey) {
            this.ip = ip;
            this.accountKey = accountKey;
        }
    }
}
