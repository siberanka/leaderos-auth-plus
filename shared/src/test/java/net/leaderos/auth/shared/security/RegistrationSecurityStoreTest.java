package net.leaderos.auth.shared.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationSecurityStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyStopsConcurrentRegistrationsAtTheLimit() throws Exception {
        DataSource dataSource = dataSource("race.db");
        RegistrationSecurityStore store = initializedStore(dataSource);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<RegistrationDecision>> tasks = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                final int number = i;
                tasks.add(() -> store.reserve("198.51.100.10", "player" + number, 3, 600));
            }
            List<Future<RegistrationDecision>> futures = executor.invokeAll(tasks);
            int allowed = 0;
            for (Future<RegistrationDecision> future : futures) {
                if (future.get().isAllowed()) {
                    allowed++;
                }
            }
            assertEquals(3, allowed, "pending registrations must consume atomic slots");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void followsTheEntireTransitiveAccountIpGraph() throws Exception {
        RegistrationSecurityStore store = initializedStore(dataSource("graph.db"));
        assertTrue(store.recordAuthenticatedAccount(uuid(), "Alpha", "10.0.0.1"));
        assertTrue(store.recordAuthenticatedAccount(uuid(), "Alpha", "10.0.0.2"));
        assertTrue(store.recordAuthenticatedAccount(uuid(), "Bravo", "10.0.0.2"));
        assertTrue(store.recordAuthenticatedAccount(uuid(), "Bravo", "10.0.0.3"));
        assertTrue(store.recordAuthenticatedAccount(uuid(), "Charlie", "10.0.0.3"));

        RegistrationDecision decision = store.reserve("10.0.0.1", "Delta", 3, 600);

        assertFalse(decision.isAllowed());
        assertEquals(RegistrationDecision.Status.LIMIT_REACHED, decision.getStatus());
        assertEquals(3, decision.getAccountCount());
        assertEquals(Arrays.asList("Alpha", "Bravo", "Charlie"), decision.getAccountNames());
    }

    @Test
    void rotatingToANewIpDoesNotResetTheLinkedAccountNetwork() throws Exception {
        RegistrationSecurityStore store = initializedStore(dataSource("rotation.db"));
        assertTrue(store.recordAuthenticatedAccount(uuid(), "Original", "203.0.113.1"));
        assertTrue(store.recordAuthenticatedAccount(uuid(), "Original", "203.0.113.99"));

        RegistrationDecision first = store.reserve("203.0.113.99", "Second", 2, 600);
        assertTrue(first.isAllowed());
        assertTrue(store.commit(first.getReservationToken(), uuid(), "Second", "203.0.113.99"));

        RegistrationDecision denied = store.reserve("203.0.113.1", "Third", 2, 600);
        assertEquals(RegistrationDecision.Status.LIMIT_REACHED, denied.getStatus());
    }

    @Test
    void rotatingIpv6PrivacyAddressesShareTheConfiguredNetworkSlot() throws Exception {
        RegistrationSecurityStore store = initializedStore(dataSource("ipv6.db"));
        RegistrationDecision first = store.reserve("2001:db8:abcd:42::1", "FirstV6", 1, 600);
        assertTrue(first.isAllowed());
        assertTrue(store.commit(first.getReservationToken(), uuid(), "FirstV6", "2001:db8:abcd:42::1"));

        RegistrationDecision rotated = store.reserve("2001:db8:abcd:42:ffff:1234:5678:9abc",
                "SecondV6", 1, 600);
        assertEquals(RegistrationDecision.Status.LIMIT_REACHED, rotated.getStatus());
    }

    @Test
    void releaseMakesAReservedSlotAvailableAgain() throws Exception {
        RegistrationSecurityStore store = initializedStore(dataSource("release.db"));
        RegistrationDecision first = store.reserve("192.0.2.1", "First", 1, 600);
        assertTrue(first.isAllowed());
        assertFalse(store.reserve("192.0.2.1", "Second", 1, 600).isAllowed());

        store.release(first.getReservationToken());

        assertTrue(store.reserve("192.0.2.1", "Second", 1, 600).isAllowed());
    }

    @Test
    void expiredReservationDoesNotConsumeASlot() throws Exception {
        DataSource source = dataSource("expiry.db");
        RegistrationSecurityStore store = initializedStore(source);
        assertTrue(store.reserve("192.0.2.8", "Expired", 1, 600).isAllowed());
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE test_registration_reservations_v2 SET expires_at = 0");
        }

        assertTrue(store.reserve("192.0.2.8", "Replacement", 1, 600).isAllowed());
    }

    @Test
    void commitRejectsAChangedIdentityWithoutConsumingTheReservation() throws Exception {
        RegistrationSecurityStore store = initializedStore(dataSource("identity.db"));
        RegistrationDecision reservation = store.reserve("192.0.2.9", "Expected", 1, 600);
        assertTrue(reservation.isAllowed());

        assertFalse(store.commit(reservation.getReservationToken(), uuid(), "Impostor", "192.0.2.9"));
        assertFalse(store.reserve("192.0.2.9", "Another", 1, 600).isAllowed());
        assertTrue(store.commit(reservation.getReservationToken(), uuid(), "Expected", "192.0.2.9"));
    }

    @Test
    void duplicateSubmissionsForOneAccountAreRejected() throws Exception {
        RegistrationSecurityStore store = initializedStore(dataSource("duplicate.db"));
        assertTrue(store.reserve("192.0.2.2", "SameName", 3, 600).isAllowed());
        RegistrationDecision duplicate = store.reserve("192.0.2.3", "samename", 3, 600);
        assertEquals(RegistrationDecision.Status.ALREADY_PENDING, duplicate.getStatus());
    }

    @Test
    void migratesLegacyGraphAndRegistrationCountersOnce() throws Exception {
        DataSource source = dataSource("migration.db");
        createLegacySchema(source);
        try (Connection connection = source.getConnection()) {
            try (PreparedStatement player = connection.prepareStatement(
                    "INSERT INTO test_playertable(uuid, name) VALUES (?, ?)")) {
                player.setString(1, uuid());
                player.setString(2, "Known");
                player.executeUpdate();
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO test_iptable(ipaddr, playerid, date) "
                        + "VALUES ('198.51.100.8', 1, datetime('now'))");
                statement.executeUpdate("INSERT INTO test_registrationtable(ipaddr, count) "
                        + "VALUES ('198.51.100.8', 2)");
            }
        }

        RegistrationSecurityStore store = new RegistrationSecurityStore(source, "test_",
                RegistrationSecurityStore.Dialect.SQLITE, 64, (message, error) -> { });
        assertTrue(store.initialize());
        assertTrue(store.initialize(), "migration must be idempotent");

        RegistrationDecision decision = store.reserve("198.51.100.8", "NewPlayer", 2, 600);
        assertEquals(RegistrationDecision.Status.LIMIT_REACHED, decision.getStatus());
        assertEquals(2, decision.getAccountCount());
    }

    @Test
    void failsClosedWhenStorageIsUnavailableOrConfigurationIsInvalid() throws Exception {
        RegistrationSecurityStore store = initializedStore(dataSource("closed.db"));
        RegistrationDecision invalidMaximum = store.reserve("192.0.2.4", "Player", 0, 600);
        RegistrationDecision invalidIp = store.reserve("invalid", "Player", 3, 600);

        assertEquals(RegistrationDecision.Status.SECURITY_ERROR, invalidMaximum.getStatus());
        assertEquals(RegistrationDecision.Status.SECURITY_ERROR, invalidIp.getStatus());

        SQLiteDataSource unavailable = new SQLiteDataSource();
        String missing = temporaryDirectory.resolve("does-not-exist").resolve("security.db")
                .toString().replace('\\', '/');
        unavailable.setUrl("jdbc:sqlite:file:" + missing + "?mode=ro");
        RegistrationSecurityStore unavailableStore = new RegistrationSecurityStore(unavailable, "test_",
                RegistrationSecurityStore.Dialect.SQLITE, 64, (message, error) -> { });
        assertFalse(unavailableStore.initialize());
        assertEquals(RegistrationDecision.Status.SECURITY_ERROR,
                unavailableStore.reserve("192.0.2.5", "Player", 3, 600).getStatus());
    }

    @Test
    void mysqlDialectSchemaAndUpsertsWork() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:mysql_dialect;MODE=MySQL;DB_CLOSE_DELAY=-1");
        createMysqlLegacySchema(source);
        RegistrationSecurityStore store = new RegistrationSecurityStore(source, "test_",
                RegistrationSecurityStore.Dialect.MYSQL, 64, (message, error) -> { });

        assertTrue(store.initialize());
        RegistrationDecision first = store.reserve("192.0.2.44", ".BedrockUser", 1, 600);
        assertTrue(first.isAllowed());
        assertTrue(store.commit(first.getReservationToken(), uuid(), ".BedrockUser", "192.0.2.44"));
        assertEquals(RegistrationDecision.Status.LIMIT_REACHED,
                store.reserve("192.0.2.44", "AnotherUser", 1, 600).getStatus());
    }

    @Test
    void mysqlDialectSerializesConcurrentReservations() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:mysql_race;MODE=MySQL;DB_CLOSE_DELAY=-1");
        createMysqlLegacySchema(source);
        RegistrationSecurityStore store = new RegistrationSecurityStore(source, "test_",
                RegistrationSecurityStore.Dialect.MYSQL, 64, (message, error) -> { });
        assertTrue(store.initialize());

        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<RegistrationDecision>> tasks = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                final int number = i;
                tasks.add(() -> store.reserve("198.51.100.20", "mysqlPlayer" + number, 3, 600));
            }
            int allowed = 0;
            for (Future<RegistrationDecision> future : executor.invokeAll(tasks)) {
                if (future.get().isAllowed()) {
                    allowed++;
                }
            }
            assertEquals(3, allowed);
        } finally {
            executor.shutdownNow();
        }
    }

    private RegistrationSecurityStore initializedStore(DataSource source) throws Exception {
        createLegacySchema(source);
        RegistrationSecurityStore store = new RegistrationSecurityStore(source, "test_",
                RegistrationSecurityStore.Dialect.SQLITE, 64, (message, error) -> { });
        assertTrue(store.initialize());
        return store;
    }

    private DataSource dataSource(String file) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + temporaryDirectory.resolve(file) + "?busy_timeout=30000");
        return dataSource;
    }

    private static void createLegacySchema(DataSource source) throws Exception {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE IF NOT EXISTS test_playertable ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, uuid VARCHAR(36) UNIQUE, name VARCHAR(32))");
            statement.execute("CREATE TABLE IF NOT EXISTS test_iptable ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, ipaddr VARCHAR(64), playerid INTEGER, date DATETIME)");
            statement.execute("CREATE TABLE IF NOT EXISTS test_registrationtable ("
                    + "ipaddr VARCHAR(64) PRIMARY KEY, count INTEGER NOT NULL)");
        }
    }

    private static void createMysqlLegacySchema(DataSource source) throws Exception {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS test_playertable ("
                    + "id BIGINT PRIMARY KEY AUTO_INCREMENT, uuid VARCHAR(36) UNIQUE, name VARCHAR(32)) ENGINE=InnoDB");
            statement.execute("CREATE TABLE IF NOT EXISTS test_iptable ("
                    + "id BIGINT PRIMARY KEY AUTO_INCREMENT, ipaddr VARCHAR(64), playerid BIGINT, date DATETIME) ENGINE=InnoDB");
            statement.execute("CREATE TABLE IF NOT EXISTS test_registrationtable ("
                    + "ipaddr VARCHAR(64) PRIMARY KEY, count INTEGER NOT NULL) ENGINE=InnoDB");
        }
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
