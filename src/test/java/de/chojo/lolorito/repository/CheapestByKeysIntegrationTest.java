/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheapestByKeysIntegrationTest extends RepositoryTestBase {

    private ItemDetail repo;

    @BeforeEach
    void setUp() {
        repo = new ItemDetail();
        query("DELETE FROM listings").single(call()).delete();
        insertWorld(66, "Odin", 7, "Light", "Europe");
        insertWorld(402, "Alpha", 7, "Light", "Europe");
    }

    @Test
    void emptyIdListReturnsEmptyMap() {
        assertTrue(repo.cheapestByKeys(List.of(), 66, null, false, 24).isEmpty());
    }

    @Test
    void missingScopeReturnsEmpty() {
        assertTrue(repo.cheapestByKeys(List.of(100), null, null, false, 24).isEmpty());
    }

    @Test
    void worldScopeReturnsPerItemMinimum() {
        seedListing(66, 100, 200, 1, false);
        seedListing(66, 100, 150, 1, false); // world 66 minimum for 100
        seedListing(402, 100, 100, 1, false); // different world — ignored
        seedListing(66, 101, 900, 1, false);
        var out = repo.cheapestByKeys(List.of(100, 101), 66, null, false, 24);
        assertEquals(150, out.get(100));
        assertEquals(900, out.get(101));
    }

    @Test
    void dcScopeCoversWholeDc() {
        seedListing(66, 100, 200, 1, false);
        seedListing(402, 100, 150, 1, false);
        var out = repo.cheapestByKeys(List.of(100), null, 7, false, 24);
        assertEquals(150, out.get(100));
    }

    @Test
    void hqNullMatchesEitherQuality() {
        seedListing(66, 100, 300, 1, true);
        seedListing(66, 100, 200, 1, false);
        var out = repo.cheapestByKeys(List.of(100), 66, null, null, 24);
        assertEquals(200, out.get(100));
    }

    @Test
    void staleSnapshotDoesNotContribute() {
        seedListing(66, 100, 150, 1, false);
        // Age the snapshot marker past the freshness horizon.
        query("UPDATE listings_updated SET updated = now() - INTERVAL '3 DAYS' WHERE world = 66 AND item = 100")
                .single(call())
                .update();
        assertTrue(repo.cheapestByKeys(List.of(100), 66, null, false, 24).isEmpty());
    }

    @Test
    void listingCountCountsPerItemAndReportsEmptyFreshBoardsAsZero() {
        seedListing(66, 100, 200, 1, false);
        seedListing(66, 100, 250, 3, false);
        seedListing(66, 100, 400, 1, true);
        seedListing(66, 101, 900, 1, false);
        query("DELETE FROM listings WHERE world = 66 AND item = 101").single(call()).delete();

        var out = repo.listingCountByKeys(List.of(100, 101, 102), 66, null, false, 24);

        assertEquals(2, out.get(100), "two NQ listings; the HQ one is filtered out");
        assertEquals(0, out.get(101), "fresh snapshot with no listings counts as zero");
        assertTrue(!out.containsKey(102), "no snapshot at all means no data");
    }

    @Test
    void listingCountCoversTheDataCenterAndIgnoresStaleBoards() {
        seedListing(66, 100, 200, 1, false);
        seedListing(402, 100, 150, 1, false);
        assertEquals(2, repo.listingCountByKeys(List.of(100), null, 7, null, 24).get(100));

        query("UPDATE listings_updated SET updated = now() - INTERVAL '3 DAYS' WHERE world = 402 AND item = 100")
                .single(call())
                .update();
        assertEquals(1, repo.listingCountByKeys(List.of(100), null, 7, null, 24).get(100));
    }

    private static void seedListing(int worldId, int itemId, int unitPrice, int quantity, boolean hq) {
        query("""
                INSERT INTO listings(world, item, hq, review_time, unit_price, quantity, total)
                VALUES (:w, :i, :hq, now(), :u, :q, :t)
                """)
                .single(call().bind("w", worldId)
                        .bind("i", itemId)
                        .bind("hq", hq)
                        .bind("u", unitPrice)
                        .bind("q", quantity)
                        .bind("t", unitPrice * quantity))
                .insert();
        query("""
                INSERT INTO listings_updated(world, item, updated) VALUES (:w, :i, now())
                ON CONFLICT (world, item) DO UPDATE SET updated = excluded.updated
                """).single(call().bind("w", worldId).bind("i", itemId)).insert();
    }
}
