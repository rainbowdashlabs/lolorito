/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import de.chojo.lolorito.entity.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionsIntegrationTest extends RepositoryTestBase {

    private Sessions sessions;

    private static void insertUser(long id) {
        // session.discord_user_id references offer_filter(user_id); create a matching row.
        query("""
                INSERT INTO offer_filter (user_id) VALUES (:user_id)
                ON CONFLICT (user_id) DO NOTHING
                """).single(call().bind("user_id", id)).insert();
    }

    private static Session sample(String id, long userId, Instant now) {
        return new Session(
                id,
                userId,
                now,
                now.plus(1, ChronoUnit.DAYS),
                "unit-test/1.0",
                new byte[] {1, 2, 3},
                new byte[] {4, 5, 6},
                now.plus(1, ChronoUnit.HOURS),
                new byte[] {7, 8, 9},
                new byte[] {10, 11, 12},
                true,
                now);
    }

    @BeforeEach
    void setUp() {
        sessions = new Sessions(dataSource);
        insertUser(1001L);
        insertUser(1002L);
        query("DELETE FROM session").single(call()).delete();
    }

    @Test
    void createFindRoundTrip() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        sessions.create(sample("s-1", 1001L, now));
        var found = sessions.find("s-1").orElseThrow();
        assertEquals("s-1", found.id());
        assertEquals(1001L, found.discordUserId());
        assertTrue(found.membershipOk());
        assertEquals(3, found.accessTokenCiphertext().length);
    }

    @Test
    void findAbsentReturnsEmpty() {
        assertTrue(sessions.find("never").isEmpty());
    }

    @Test
    void touchExpiryUpdatesRow() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        sessions.create(sample("s-1", 1001L, now));
        var newExp = now.plus(90, ChronoUnit.DAYS);
        sessions.touchExpiry("s-1", newExp);
        assertEquals(newExp, sessions.find("s-1").orElseThrow().expiresAt());
    }

    @Test
    void updateTokensReplacesCiphertext() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        sessions.create(sample("s-1", 1001L, now));
        sessions.updateTokens(
                "s-1",
                new byte[] {99, 99},
                new byte[] {88, 88},
                now.plus(2, ChronoUnit.HOURS),
                new byte[] {77, 77},
                new byte[] {66, 66});
        var updated = sessions.find("s-1").orElseThrow();
        assertEquals(2, updated.accessTokenCiphertext().length);
        assertEquals(99, updated.accessTokenCiphertext()[0]);
        assertEquals(88, updated.accessTokenIv()[0]);
        assertEquals(77, updated.refreshTokenCiphertext()[0]);
        assertEquals(66, updated.refreshTokenIv()[0]);
    }

    @Test
    void updateMembershipFlagFlipsAndStampsCheckedAt() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        sessions.create(sample("s-1", 1001L, now));
        var later = now.plus(30, ChronoUnit.MINUTES);
        sessions.updateMembership("s-1", false, later);
        var row = sessions.find("s-1").orElseThrow();
        assertFalse(row.membershipOk());
        assertEquals(later, row.membershipCheckedAt());
    }

    @Test
    void deleteRemovesSingleRow() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        sessions.create(sample("s-1", 1001L, now));
        sessions.create(sample("s-2", 1002L, now));
        sessions.delete("s-1");
        assertTrue(sessions.find("s-1").isEmpty());
        assertTrue(sessions.find("s-2").isPresent());
    }

    @Test
    void deleteAllForUserRemovesJustThatUser() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        sessions.create(sample("s-1", 1001L, now));
        sessions.create(sample("s-2", 1001L, now));
        sessions.create(sample("s-3", 1002L, now));
        int deleted = sessions.deleteAllForUser(1001L);
        assertEquals(2, deleted);
        assertTrue(sessions.find("s-3").isPresent());
    }

    @Test
    void deleteExpiredDropsRowsPastCutoff() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var fresh = sample("s-fresh", 1001L, now);
        var stale = new Session(
                "s-stale",
                1002L,
                now.minus(10, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS),
                "test",
                new byte[] {1},
                new byte[] {2},
                now,
                new byte[] {3},
                new byte[] {4},
                true,
                now);
        sessions.create(fresh);
        sessions.create(stale);
        int deleted = sessions.deleteExpired(now);
        assertEquals(1, deleted);
        assertTrue(sessions.find("s-stale").isEmpty());
        assertTrue(sessions.find("s-fresh").isPresent());
    }

    @Test
    void dueForBackgroundRecheckPicksStaleAndOrdersOldestFirst() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // Old
        var a = new Session(
                "s-old",
                1001L,
                now,
                now.plus(1, ChronoUnit.DAYS),
                "ua",
                new byte[] {1},
                new byte[] {1},
                now,
                new byte[] {1},
                new byte[] {1},
                true,
                now.minus(2, ChronoUnit.HOURS));
        var b = new Session(
                "s-recent",
                1002L,
                now,
                now.plus(1, ChronoUnit.DAYS),
                "ua",
                new byte[] {1},
                new byte[] {1},
                now,
                new byte[] {1},
                new byte[] {1},
                true,
                now.minus(1, ChronoUnit.MINUTES));
        sessions.create(a);
        sessions.create(b);
        var due = sessions.dueForBackgroundRecheck(now.minus(30, ChronoUnit.MINUTES), 10);
        // Only s-old passes the "checked before threshold" test.
        assertEquals(1, due.size());
        assertEquals("s-old", due.getFirst().id());
    }

    @Test
    void dueForBackgroundRecheckSkipsRevokedSessions() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var revoked = new Session(
                "s-revoked",
                1001L,
                now,
                now.plus(1, ChronoUnit.DAYS),
                "ua",
                new byte[] {1},
                new byte[] {1},
                now,
                new byte[] {1},
                new byte[] {1},
                false,
                now.minus(1, ChronoUnit.HOURS));
        sessions.create(revoked);
        var due = sessions.dueForBackgroundRecheck(now, 10);
        assertTrue(due.isEmpty(), "revoked sessions (membership_ok=false) never come back from the sweeper");
    }
}
