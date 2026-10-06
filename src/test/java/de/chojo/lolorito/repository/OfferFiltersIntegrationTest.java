/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import de.chojo.lolorito.entity.OfferFilterRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferFiltersIntegrationTest extends RepositoryTestBase {

    private OfferFilters repo;

    @BeforeEach
    void setUp() {
        repo = new OfferFilters(dataSource);
        query("DELETE FROM offer_filter").single(call()).delete();
    }

    @Test
    void findAbsentReturnsEmpty() {
        assertTrue(repo.find(999).isEmpty());
    }

    @Test
    void upsertInsertsFirstThenUpdatesInPlace() {
        var initial = new OfferFilterRow(66, 1000, 500, 1.5, 2, 5.0, 6.0, 7.0, 8, 9, 100, 2000, "DATA_CENTER");
        repo.upsert(42L, initial);
        assertEquals(initial, repo.find(42L).orElseThrow());

        var updated = new OfferFilterRow(77, 250, 100, 2.0, 6, 10.0, 12.0, 14.0, 16, 18, 200, 4000, "REGION");
        repo.upsert(42L, updated);
        var found = repo.find(42L).orElseThrow();
        assertEquals(77, found.worldId());
        assertEquals(250, found.offerLimit());
        assertEquals(2.0, found.factor(), 1e-9);
        assertEquals("REGION", found.target());
    }

    @Test
    void multipleUsersAreIndependent() {
        var a = new OfferFilterRow(1, 100, 10, 1.0, 1, 0, 0, 0, 0, 0, 0, 0, "REGION");
        var b = new OfferFilterRow(2, 200, 20, 2.0, 2, 0, 0, 0, 0, 0, 0, 0, "DATA_CENTER");
        repo.upsert(1L, a);
        repo.upsert(2L, b);
        assertEquals(a, repo.find(1L).orElseThrow());
        assertEquals(b, repo.find(2L).orElseThrow());
    }

    @Test
    void findReturnsExactlyWhatWasWritten() {
        var row = new OfferFilterRow(80, 750, 1500, 3.25, 24, 12.5, 34.5, 56.5, 100, 200, 500, 9999, "REGION", 120_000, 35);
        repo.upsert(1234L, row);
        var found = repo.find(1234L).orElseThrow();
        assertEquals(row, found);
    }
}
