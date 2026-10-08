package net.leaderos.auth.shared.security;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionPolicyTest {

    private static final List<String> NOBODY = Collections.emptyList();

    @Test
    void sameProfileFromSameAddressMayReplaceTheOldConnection() {
        UUID id = UUID.randomUUID();
        assertTrue(DuplicateLoginPolicy.mayReplace(id, "203.0.113.7", id, "203.0.113.7"));
        assertTrue(DuplicateLoginPolicy.mayReplace(id, "::ffff:203.0.113.7", id, "203.0.113.7"));
        assertTrue(DuplicateLoginPolicy.mayReplace(id, "2001:db8::1", id, "2001:0db8:0:0::1"));
    }

    @Test
    void otherAddressesOrProfilesAreRefused() {
        UUID id = UUID.randomUUID();
        assertFalse(DuplicateLoginPolicy.mayReplace(id, "203.0.113.7", id, "203.0.113.8"));
        assertFalse(DuplicateLoginPolicy.mayReplace(id, "203.0.113.7", UUID.randomUUID(), "203.0.113.7"));
        // Same /64 is not the same address.
        assertFalse(DuplicateLoginPolicy.mayReplace(id, "2001:db8::1", id, "2001:db8::2"));
        assertFalse(DuplicateLoginPolicy.mayReplace(id, null, id, "203.0.113.7"));
        assertFalse(DuplicateLoginPolicy.mayReplace(null, "203.0.113.7", null, "203.0.113.7"));
        assertFalse(DuplicateLoginPolicy.mayReplace(id, "example.com", id, "example.com"));
    }

    @Test
    void limitCountsOnlinePlayersAndPendingLogins() {
        IpConnectionTracker tracker = new IpConnectionTracker(30_000);
        assertTrue(tracker.tryAdmit("A", "198.51.100.1", 2, NOBODY, 0));
        assertTrue(tracker.tryAdmit("B", "198.51.100.1", 2, NOBODY, 0));
        assertFalse(tracker.tryAdmit("C", "198.51.100.1", 2, NOBODY, 0), "two pending logins fill the limit");
        assertTrue(tracker.tryAdmit("D", "198.51.100.2", 2, NOBODY, 0), "other addresses are independent");

        tracker.joined("A");
        tracker.joined("B");
        assertFalse(tracker.tryAdmit("C", "198.51.100.1", 2, Arrays.asList("A", "B"), 1));
        assertTrue(tracker.tryAdmit("C", "198.51.100.1", 2, Collections.singletonList("A"), 2),
                "a player that left frees its slot without any counter");
    }

    @Test
    void reconnectOfTheSamePlayerDoesNotTakeASecondSlot() {
        IpConnectionTracker tracker = new IpConnectionTracker(30_000);
        assertTrue(tracker.tryAdmit("Steve", "198.51.100.1", 1, NOBODY, 0));
        tracker.joined("Steve");
        // The old connection is still listed while the client reconnects.
        assertTrue(tracker.tryAdmit("steve", "198.51.100.1", 1, Collections.singletonList("Steve"), 10));
        assertFalse(tracker.tryAdmit("Alex", "198.51.100.1", 1, Collections.singletonList("Steve"), 10));
    }

    @Test
    void serverSwitchesAndRepeatedEventsNeverDoubleCount() {
        IpConnectionTracker tracker = new IpConnectionTracker(30_000);
        assertTrue(tracker.tryAdmit("Steve", "198.51.100.1", 2, NOBODY, 0));
        tracker.joined("Steve");
        // A proxy transfer between backends fires no login; duplicated quit/join callbacks are harmless.
        tracker.joined("Steve");
        tracker.release("Steve");
        tracker.release("Steve");
        assertEquals(0, tracker.pendingCount());
        assertTrue(tracker.tryAdmit("Alex", "198.51.100.1", 2, Collections.singletonList("Steve"), 1));
        assertFalse(tracker.tryAdmit("Bob", "198.51.100.1", 2, Collections.singletonList("Steve"), 1));
    }

    @Test
    void failedOrAbandonedLoginsReleaseTheirSlot() {
        IpConnectionTracker tracker = new IpConnectionTracker(10_000);
        assertTrue(tracker.tryAdmit("A", "198.51.100.1", 1, NOBODY, 0));
        assertFalse(tracker.tryAdmit("B", "198.51.100.1", 1, NOBODY, 1));
        tracker.release("A");
        assertTrue(tracker.tryAdmit("B", "198.51.100.1", 1, NOBODY, 2));
        // B never finishes logging in: the slot comes back after the timeout.
        assertFalse(tracker.tryAdmit("C", "198.51.100.1", 1, NOBODY, 9_000));
        assertTrue(tracker.tryAdmit("C", "198.51.100.1", 1, NOBODY, 10_003));
    }

    @Test
    void pendingPlayerAlreadyListedOnlineIsCountedOnce() {
        IpConnectionTracker tracker = new IpConnectionTracker(30_000);
        assertTrue(tracker.tryAdmit("A", "198.51.100.1", 2, NOBODY, 0));
        // A is in the player list but its join callback did not run yet.
        assertTrue(tracker.tryAdmit("B", "198.51.100.1", 2, Collections.singletonList("A"), 1));
        assertFalse(tracker.tryAdmit("C", "198.51.100.1", 2, Collections.singletonList("A"), 2));
    }

    @Test
    void simultaneousLoginsCannotExceedTheLimit() throws Exception {
        IpConnectionTracker tracker = new IpConnectionTracker(30_000);
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                final String name = "bot" + i;
                tasks.add(() -> tracker.tryAdmit(name, "198.51.100.9", 3, NOBODY, 0));
            }
            int admitted = 0;
            for (Future<Boolean> future : executor.invokeAll(tasks)) {
                if (future.get()) {
                    admitted++;
                }
            }
            assertEquals(3, admitted);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void disabledLimitAdmitsEveryone() {
        IpConnectionTracker tracker = new IpConnectionTracker(30_000);
        for (int i = 0; i < 10; i++) {
            assertTrue(tracker.tryAdmit("p" + i, "198.51.100.1", 0, NOBODY, 0));
        }
        assertEquals(0, tracker.pendingCount());
    }

    @Test
    void ipv4MappedAddressesShareTheLimit() {
        IpConnectionTracker tracker = new IpConnectionTracker(30_000);
        assertTrue(tracker.tryAdmit("A", "::ffff:198.51.100.1", 1, NOBODY, 0));
        assertFalse(tracker.tryAdmit("B", "198.51.100.1", 1, NOBODY, 0));
    }
}
