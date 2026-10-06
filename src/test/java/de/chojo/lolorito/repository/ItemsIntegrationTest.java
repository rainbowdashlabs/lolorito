/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import de.chojo.lolorito.entity.ItemStat;
import de.chojo.lolorito.entity.OfferFilterRow;
import de.chojo.lolorito.entity.SearchScope;
import de.chojo.lolorito.entity.SortOrder;
import de.chojo.lolorito.entity.TopFilter;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemsIntegrationTest extends RepositoryTestBase {

    private static final World HOME = Worlds.worldById(66); // Odin (Light, DC 7)
    private static final World ALPHA = Worlds.worldById(402); // Alpha (Light)
    private static final int LIGHT_DC = 7;
    private static final int CHAOS_DC = 6;
    private static final NameSupplier NAMES = NameSupplier.EMPTY;
    private Items repo;

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
        seedListingUpdated(worldId, itemId, Instant.now());
    }

    // -- currentListings ---------------------------------------------------

    private static void seedListingUpdated(int worldId, int itemId, Instant when) {
        query("""
                INSERT INTO listings_updated(world, item, updated) VALUES (:w, :i, :u)
                ON CONFLICT(world, item) DO UPDATE SET updated = excluded.updated
                """)
                .single(call().bind("w", worldId).bind("i", itemId).bind("u", when, INSTANT_TIMESTAMP))
                .insert();
    }

    private static void seedListingsViewed(int worldId, int itemId) {
        query("""
                INSERT INTO listings_viewed(world, item)
                VALUES (:w, :i)
                ON CONFLICT (world, item, day) DO UPDATE SET count = listings_viewed.count + 1
                """).single(call().bind("w", worldId).bind("i", itemId)).insert();
    }

    // -- stats -------------------------------------------------------------

    private static void seedSale(int worldId, int itemId, int unitPrice, int qty, boolean hq, Instant when) {
        query("""
                INSERT INTO sales(world, item, hq, sold, unit_price, quantity, total)
                VALUES (:w, :i, :hq, :s, :u, :q, :t)
                """)
                .single(call().bind("w", worldId)
                        .bind("i", itemId)
                        .bind("hq", hq)
                        .bind("s", when, INSTANT_TIMESTAMP)
                        .bind("u", unitPrice)
                        .bind("q", qty)
                        .bind("t", unitPrice * qty))
                .insert();
    }

    /**
     * Refresh in the same order {@code DataRefreshWorker} uses in prod.
     */
    private static void refreshViews() {
        List<String> views = List.of(
                "world_item_listings",
                "world_item_sales",
                "world_item_views",
                "world_sales",
                "world_views",
                "world_item_popularity",
                "world_items");
        for (String v : views) {
            query("REFRESH MATERIALIZED VIEW " + v).single(call()).update();
        }
    }

    // -- arbitrageListings -------------------------------------------------

    @BeforeEach
    void setUp() {
        repo = new Items(NAMES);
        query("DELETE FROM listings").single(call()).delete();
        query("DELETE FROM listings_updated").single(call()).delete();
        query("DELETE FROM listings_viewed").single(call()).delete();
        query("DELETE FROM sales").single(call()).delete();
        insertWorld(66, "Odin", LIGHT_DC, "Light", "Europe");
        insertWorld(402, "Alpha", LIGHT_DC, "Light", "Europe");
        insertWorld(97, "Ragnarok", CHAOS_DC, "Chaos", "Europe");
    }

    @Test
    void currentListingsReturnsEveryRowForItemOnDc() {
        seedListing(66, 100, 500, 3, false);
        seedListing(402, 100, 400, 2, false);
        seedListing(97, 100, 300, 1, false);
        var out = repo.currentListings(100, LIGHT_DC, null);
        assertEquals(2, out.size(), "only rows on DC 10");
    }

    // -- top ---------------------------------------------------------------

    @Test
    void currentListingsRespectsHqFilter() {
        seedListing(66, 100, 500, 3, false);
        seedListing(66, 100, 500, 3, true);
        assertEquals(1, repo.currentListings(100, LIGHT_DC, true).size());
        assertEquals(1, repo.currentListings(100, LIGHT_DC, false).size());
        assertEquals(2, repo.currentListings(100, LIGHT_DC, null).size());
    }

    @Test
    void statsReturnsSentinelWhenNothingMatches() {
        var stat = repo.stats(HOME, Item.ofId(999), false);
        assertNotNull(stat);
        assertEquals(0, stat.sales());
        assertEquals(0, stat.marketVolume(), 1e-9);
        assertEquals(999, stat.item().id());
    }

    @Test
    void statsReturnsAggregatedRowWhenPopularityHasData() {
        seedListing(66, 100, 500, 3, false);
        seedSale(66, 100, 500, 3, false, Instant.now().minus(1, ChronoUnit.HOURS));
        refreshViews();
        var stat = repo.stats(HOME, Item.ofId(100), false);
        assertEquals(66, stat.world().id());
        assertEquals(100, stat.item().id());
        assertTrue(stat.sales() >= 3);
    }

    // -- seeding helpers ---------------------------------------------------

    @Test
    void arbitrageListingsReturnsRowsWhenSourcePricedBelowHome() {
        // Home listing very high, source listing very low → factor > 2 by default filter.
        seedListing(66, 100, 5000, 5, false);
        seedListing(402, 100, 100, 5, false);
        seedListingUpdated(402, 100, Instant.now().minus(10, ChronoUnit.MINUTES));
        seedListingUpdated(66, 100, Instant.now());
        seedSale(66, 100, 5000, 5, false, Instant.now().minus(1, ChronoUnit.HOURS));
        seedSale(402, 100, 100, 5, false, Instant.now().minus(1, ChronoUnit.HOURS));
        seedListingsViewed(66, 100);
        refreshViews();

        // Passing -1 for the "minimum-of-X" checks — the SQL uses strict >, so 0 would exclude
        // the row when views=0.
        // effective_profit compares against `least(daily_sales, volume) * (home_price - min_source_price)`
        // and daily_sales is (integer) sales/7, so a small seed yields 0. Use -1 to skip that gate.
        var filter = new OfferFilterRow(66, 1000, 50, 2.0, 24, -1.0, -1.0, -1.0, -1, -1, 1000, -1, "DATA_CENTER");
        var out = repo.arbitrageListings(filter, HOME);
        assertEquals(1, out.size());
        assertEquals(ALPHA.id(), out.getFirst().world().id());
        assertEquals(100, out.getFirst().price().pricePerUnit());
    }

    @Test
    void arbitrageListingsHonoursRegionTarget() {
        seedListing(66, 100, 5000, 5, false);
        seedListing(97, 100, 100, 5, false);
        seedListingUpdated(97, 100, Instant.now().minus(10, ChronoUnit.MINUTES));
        seedListingUpdated(66, 100, Instant.now());
        seedSale(66, 100, 5000, 5, false, Instant.now().minus(1, ChronoUnit.HOURS));
        seedSale(97, 100, 100, 5, false, Instant.now().minus(1, ChronoUnit.HOURS));
        seedListingsViewed(66, 100);
        refreshViews();

        var region = new OfferFilterRow(66, 1000, 50, 2.0, 24, -1.0, -1.0, -1.0, -1, -1, 1000, -1, "REGION");
        assertEquals(1, repo.arbitrageListings(region, HOME).size(), "REGION target reaches Chaos too");

        var dc = new OfferFilterRow(66, 1000, 50, 2.0, 24, -1.0, -1.0, -1.0, -1, -1, 1000, -1, "DATA_CENTER");
        assertTrue(repo.arbitrageListings(dc, HOME).isEmpty(), "DATA_CENTER stays inside Light");
    }

    @Test
    void topReturnsRowsFilteredByScopeAndOrdered() {
        seedListing(66, 100, 500, 3, false);
        seedListing(66, 101, 200, 1, false);
        seedSale(66, 100, 500, 3, false, Instant.now().minus(1, ChronoUnit.HOURS));
        seedSale(66, 101, 200, 1, false, Instant.now().minus(1, ChronoUnit.HOURS));
        refreshViews();

        var topFilter = new TopFilter(SearchScope.WORLD, SortOrder.SALES, null, null, null, null, null, null, null);
        var out = repo.top(topFilter, 66);
        assertTrue(out.size() >= 2);
    }

    @Test
    void topHonoursDataCenterScope() {
        seedListing(402, 100, 500, 3, false);
        seedSale(402, 100, 500, 3, false, Instant.now().minus(1, ChronoUnit.HOURS));
        refreshViews();

        var topFilter = new TopFilter(
                SearchScope.DATACENTER, SortOrder.MARKET_VOLUME, null, null, null, null, null, null, null);
        var out = repo.top(topFilter, LIGHT_DC);
        assertTrue(!out.isEmpty());
    }

    @Test
    void topRespectsHqAndMinSalesFilters() {
        seedListing(66, 100, 500, 3, false);
        seedListing(66, 101, 200, 1, true);
        seedSale(66, 100, 500, 30, false, Instant.now().minus(1, ChronoUnit.HOURS));
        seedSale(66, 101, 200, 1, true, Instant.now().minus(1, ChronoUnit.HOURS));
        refreshViews();

        var hqOnly = new TopFilter(SearchScope.WORLD, SortOrder.SALES, true, null, null, null, null, null, null);
        var out = repo.top(hqOnly, 66);
        assertTrue(out.stream().allMatch(ItemStat::hq));

        var minSales = new TopFilter(SearchScope.WORLD, SortOrder.SALES, null, 10, null, null, null, null, null);
        var filtered = repo.top(minSales, 66);
        assertTrue(filtered.stream().allMatch(s -> s.sales() >= 10));
    }
}
