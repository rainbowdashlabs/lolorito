/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.PriceDistribution;
import de.chojo.lolorito.value.SaleRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemDetailIntegrationTest extends RepositoryTestBase {

    private ItemDetail repo;
    private MarketModels marketModels;

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
    }

    @BeforeEach
    void setUp() {
        repo = new ItemDetail();
        marketModels = new MarketModels(dataSource);
        query("DELETE FROM listings").single(call()).delete();
        query("DELETE FROM market_model").single(call()).delete();
        insertWorld(66, "Odin", 7, "Light", "Europe");
        insertWorld(402, "Alpha", 7, "Light", "Europe");
    }

    @Test
    void homeModelReturnsPersistedRow() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        marketModels.upsert(new MarketModel(
                100,
                66,
                false,
                new PriceDistribution(6.9, 0.3, 42),
                new SaleRate(1.5, 1.0, 0.6),
                0.0,
                0.0,
                42,
                true,
                false,
                now));
        var found = repo.homeModel(66, 100, false).orElseThrow();
        assertEquals(6.9, found.price().mu(), 1e-9);
        assertEquals(42, found.sampleCount());
    }

    @Test
    void homeModelAbsentReturnsEmpty() {
        assertTrue(repo.homeModel(66, 999, false).isEmpty());
    }

    @Test
    void cheapestByItemReturnsMinimumPerItem() {
        seedListing(66, 100, 500, 3, false);
        seedListing(402, 100, 400, 5, false);
        seedListing(66, 101, 200, 1, false);
        seedListing(66, 100, 350, 2, true); // wrong hq, should be ignored
        var out = repo.cheapestByItem(7, List.of(100, 101, 202), false, 24);
        assertEquals(400, out.get(100));
        assertEquals(200, out.get(101));
        assertTrue(!out.containsKey(202), "items with no listing don't get an entry");
    }

    @Test
    void cheapestByItemReturnsEmptyOnEmptyInput() {
        assertEquals(Map.of(), repo.cheapestByItem(7, List.of(), false, 24));
    }

    @Test
    void listingsReturnsRegionListingsOrderedByPrice() {
        seedListing(66, 100, 500, 3, false);
        seedListing(402, 100, 200, 1, false);
        seedListing(66, 100, 900, 4, false);
        var rows = repo.listings(100, 7, false, 10);
        assertEquals(3, rows.size());
        assertEquals(200, rows.get(0).unitPrice(), "cheapest first");
        assertEquals(500, rows.get(1).unitPrice());
        assertEquals(900, rows.get(2).unitPrice());
        assertNotNull(rows.get(0).reviewedAt());
    }

    @Test
    void listingsRespectsHqFlag() {
        seedListing(66, 100, 500, 3, false);
        seedListing(66, 100, 500, 3, true);
        assertEquals(1, repo.listings(100, 7, true, 10).size());
        assertEquals(1, repo.listings(100, 7, false, 10).size());
    }

    @Test
    void cheapestPriceWorldScopeIgnoresOtherWorlds() {
        seedListing(66, 100, 200, 1, false);
        seedListing(402, 100, 100, 1, false); // cheaper on a different world
        assertEquals(200, repo.cheapestPrice(100, 66, null, false).orElseThrow());
    }

    @Test
    void cheapestPriceDataCenterScopeCoversWholeDc() {
        seedListing(66, 100, 200, 1, false);
        seedListing(402, 100, 150, 1, false);
        assertEquals(150, repo.cheapestPrice(100, null, 7, false).orElseThrow());
    }

    @Test
    void cheapestPriceHqNullMatchesEitherQuality() {
        seedListing(66, 100, 300, 1, true);
        seedListing(66, 100, 150, 1, false);
        assertEquals(150, repo.cheapestPrice(100, 66, null, null).orElseThrow());
    }

    @Test
    void cheapestPriceEmptyWhenNoListingsMatch() {
        assertTrue(repo.cheapestPrice(999, 66, null, false).isEmpty());
    }

    @Test
    void cheapestPriceEmptyWhenScopeMissing() {
        assertTrue(repo.cheapestPrice(100, null, null, false).isEmpty());
    }
}
