package net.leaderos.auth.shared.security;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockLinkStoreTest {

    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final String XUID = "2535428504466012";

    @TempDir
    Path directory;

    @Test
    void bindsOnceAndTrustsOnlyTheSameXuid() {
        BedrockLinkStore store = sqlite("links.db");
        assertEquals(BedrockLinkStore.BindResult.BOUND, store.bind(".Steve", XUID, 0));

        BedrockLinkStore.Link link = store.find(".steve");
        assertNotNull(link);
        assertTrue(link.trusts(XUID, 30 * DAY, DAY));
        assertFalse(link.trusts("2535428504466013", 30 * DAY, DAY), "another Xbox account is not trusted");
        assertFalse(link.trusts(null, 30 * DAY, DAY));
    }

    @Test
    void anotherXuidCannotTakeOverABoundAccount() {
        BedrockLinkStore store = sqlite("conflict.db");
        assertEquals(BedrockLinkStore.BindResult.BOUND, store.bind("Victim", XUID, 0));
        assertEquals(BedrockLinkStore.BindResult.CONFLICT, store.bind("victim", "1111111111111111", 10));
        assertEquals(XUID, store.find("VICTIM").getXuid());
    }

    @Test
    void passwordLoginsRefreshAndOldVerificationsExpire() {
        BedrockLinkStore store = sqlite("expiry.db");
        store.bind("Alex", XUID, 0);
        assertFalse(store.find("Alex").trusts(XUID, 30 * DAY, 31 * DAY), "expired without a password login");
        assertEquals(BedrockLinkStore.BindResult.REFRESHED, store.bind("Alex", XUID, 29 * DAY));
        assertTrue(store.find("Alex").trusts(XUID, 30 * DAY, 31 * DAY));
        assertFalse(store.find("Alex").trusts(XUID, 0, 31 * DAY), "a zero max age never trusts");
        // A clock far in the past cannot make a verification look fresh forever.
        assertFalse(new BedrockLinkStore.Link(XUID, 0, 100 * DAY).trusts(XUID, 30 * DAY, DAY));
    }

    @Test
    void unbindRemovesTrust() {
        BedrockLinkStore store = sqlite("unbind.db");
        store.bind("Alex", XUID, 0);
        assertTrue(store.unbind("ALEX"));
        assertNull(store.find("Alex"));
        assertFalse(store.unbind("Alex"));
        assertEquals(BedrockLinkStore.BindResult.BOUND, store.bind("Alex", "1234", 1));
    }

    @Test
    void invalidInputIsRejected() {
        BedrockLinkStore store = sqlite("invalid.db");
        assertEquals(BedrockLinkStore.BindResult.ERROR, store.bind("Alex", "not-a-xuid", 0));
        assertEquals(BedrockLinkStore.BindResult.ERROR, store.bind("Alex", "0", 0));
        assertEquals(BedrockLinkStore.BindResult.ERROR, store.bind("Alex", "123456789012345678901", 0));
        assertEquals(BedrockLinkStore.BindResult.ERROR, store.bind("", XUID, 0));
        assertEquals(BedrockLinkStore.BindResult.ERROR, store.bind("bad\u0000name", XUID, 0));
        assertNull(store.find(null));
    }

    @Test
    void concurrentBindsOfOneAccountKeepASingleXuid() throws Exception {
        BedrockLinkStore store = mysqlDialect("race");
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<BedrockLinkStore.BindResult>> tasks = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                final String xuid = String.valueOf(1000 + i);
                tasks.add(() -> store.bind("Shared", xuid, 0));
            }
            int bound = 0;
            for (Future<BedrockLinkStore.BindResult> future : executor.invokeAll(tasks)) {
                if (future.get() == BedrockLinkStore.BindResult.BOUND) {
                    bound++;
                }
            }
            assertEquals(1, bound);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void unreachableDatabaseFailsClosed() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:closed_links;MODE=MySQL;DB_CLOSE_DELAY=-1");
        BedrockLinkStore store = new BedrockLinkStore(source, "test_", RegistrationSecurityStore.Dialect.MYSQL,
                (message, error) -> { });
        // Table never created.
        assertNull(store.find("Alex"));
        assertEquals(BedrockLinkStore.BindResult.ERROR, store.bind("Alex", XUID, 0));
    }

    private BedrockLinkStore sqlite(String file) {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve(file) + "?busy_timeout=30000");
        return initialized(source, RegistrationSecurityStore.Dialect.SQLITE);
    }

    private BedrockLinkStore mysqlDialect(String name) {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return initialized(source, RegistrationSecurityStore.Dialect.MYSQL);
    }

    private static BedrockLinkStore initialized(DataSource source, RegistrationSecurityStore.Dialect dialect) {
        BedrockLinkStore store = new BedrockLinkStore(source, "test_", dialect, (message, error) -> { });
        assertTrue(store.initialize());
        return store;
    }
}
