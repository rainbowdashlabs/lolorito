/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetainerSalesIntegrationTest extends RepositoryTestBase {

    private RetainerSales repo;

    @BeforeEach
    void setUp() {
        repo = new RetainerSales();
        query("DELETE FROM retainer_sale").single(call()).delete();
    }

    @Test
    void insertRoundTripsThroughFindById() {
        var row = repo.insert(42L, "Wolfe", 5057, 66, false, 250L, 3, Instant.now(), "flip");
        assertNotNull(row);
        assertEquals(42L, row.discordUserId());
        assertEquals("Wolfe", row.retainerName());
        assertEquals(5057, row.itemId());

        var reread = repo.byId(row.id(), 42L);
        assertTrue(reread.isPresent());
        assertEquals(row.itemId(), reread.get().itemId());
    }

    @Test
    void recentIsPerUserAndNewestFirst() {
        repo.insert(1L, null, 100, 66, false, 200L, 1, Instant.now().minusSeconds(120), null);
        repo.insert(1L, null, 101, 66, false, 300L, 1, Instant.now(), null);
        repo.insert(2L, null, 999, 66, false, 500L, 1, Instant.now(), null);
        var mine = repo.recent(1L, 10);
        assertEquals(2, mine.size());
        assertEquals(101, mine.getFirst().itemId(), "newest first");
    }

    @Test
    void byIdOnlyReturnsRowsOwnedByTheCaller() {
        var row = repo.insert(1L, null, 100, 66, false, 200L, 1, Instant.now(), null);
        assertTrue(repo.byId(row.id(), 1L).isPresent());
        assertFalse(repo.byId(row.id(), 999L).isPresent(), "other user gets nothing back");
    }

    @Test
    void deleteRefusesToTouchAnotherUsersRow() {
        var row = repo.insert(1L, null, 100, 66, false, 200L, 1, Instant.now(), null);
        assertFalse(repo.delete(row.id(), 999L));
        assertTrue(repo.byId(row.id(), 1L).isPresent(), "row survives");
        assertTrue(repo.delete(row.id(), 1L));
        assertFalse(repo.byId(row.id(), 1L).isPresent());
    }
}
