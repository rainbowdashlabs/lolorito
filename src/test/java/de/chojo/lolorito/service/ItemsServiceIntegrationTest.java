/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.entity.BotUser;
import de.chojo.lolorito.entity.OfferFilterRow;
import de.chojo.lolorito.entity.SearchScope;
import de.chojo.lolorito.entity.SortOrder;
import de.chojo.lolorito.entity.TopFilter;
import de.chojo.lolorito.repository.Items;
import de.chojo.lolorito.repository.OfferFilters;
import de.chojo.universalis.provider.NameSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemsServiceIntegrationTest extends ServiceIntegrationTestBase {

    private ItemsService service;
    private OfferFilters offerFilters;

    private static OfferFilterRow homeFilter(int worldId, String target) {
        return new OfferFilterRow(worldId, 100, 100, 1.2, 24, -1.0, -1.0, -1.0, -1, -1, 100, -1, target);
    }

    private static void seedListing(int worldId, int itemId, int unitPrice, int qty, boolean hq) {
        query("""
                INSERT INTO listings(world, item, hq, review_time, unit_price, quantity, total)
                VALUES (:w, :i, :hq, now(), :u, :q, :t)
                """)
                .single(call().bind("w", worldId)
                        .bind("i", itemId)
                        .bind("hq", hq)
                        .bind("u", unitPrice)
                        .bind("q", qty)
                        .bind("t", unitPrice * qty))
                .insert();
        seedListingUpdated(worldId, itemId, Instant.now());
    }

    private static void seedListingUpdated(int worldId, int itemId, Instant when) {
        query("""
                INSERT INTO listings_updated(world, item, updated) VALUES (:w, :i, :u)
                ON CONFLICT(world, item) DO UPDATE SET updated = excluded.updated
                """)
                .single(call().bind("w", worldId).bind("i", itemId).bind("u", when, INSTANT_TIMESTAMP))
                .insert();
    }

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

    private static void seedListingsViewed(int worldId, int itemId) {
        query("""
                INSERT INTO listings_viewed(world, item)
                VALUES (:w, :i)
                ON CONFLICT (world, item, day) DO UPDATE SET count = listings_viewed.count + 1
                """).single(call().bind("w", worldId).bind("i", itemId)).insert();
    }

    private static void refreshViews() {
        for (String v : List.of(
                "world_item_listings",
                "world_item_sales",
                "world_item_views",
                "world_sales",
                "world_views",
                "world_item_popularity",
                "world_items")) {
            query("REFRESH MATERIALIZED VIEW " + v).single(call()).update();
        }
    }

    @BeforeEach
    void setUp() {
        offerFilters = new OfferFilters(dataSource);
        var filters = new FilterService(offerFilters);
        service = new ItemsService(new Items(NameSupplier.EMPTY), filters, NameSupplier.EMPTY);
        query("DELETE FROM listings").single(call()).delete();
        query("DELETE FROM listings_updated").single(call()).delete();
        query("DELETE FROM listings_viewed").single(call()).delete();
        query("DELETE FROM sales").single(call()).delete();
        query("DELETE FROM offer_filter").single(call()).delete();
        insertWorld(66, "Odin", 7, "Light", "Europe");
        insertWorld(402, "Alpha", 7, "Light", "Europe");
        insertWorld(97, "Ragnarok", 6, "Chaos", "Europe");
    }

    @Test
    void listingForAssemblesPerWorldGroupsWithStats() {
        offerFilters.upsert(1L, homeFilter(66, "DATA_CENTER"));
        seedListing(66, 100, 500, 3, false);
        seedListing(402, 100, 400, 2, false);

        var listing = service.listingFor(new BotUser(1L), 100, null);
        assertEquals(2, listing.offers().size(), "one entry per source world");
    }

    @Test
    void listingForReturnsEmptyOffersWhenHomeWorldMissing() {
        // Default filter row worldId = -1 → home world is unset.
        var listing = service.listingFor(new BotUser(9999L), 100, false);
        assertTrue(listing.offers().isEmpty());
    }

    @Test
    void bestOffersForAssemblesOfferPerItem() {
        offerFilters.upsert(1L, homeFilter(66, "DATA_CENTER"));
        seedListing(66, 100, 5000, 5, false);
        seedListing(402, 100, 100, 5, false);
        seedListingUpdated(402, 100, Instant.now().minus(1, ChronoUnit.HOURS));
        seedListingUpdated(66, 100, Instant.now());
        seedSale(66, 100, 5000, 5, false, Instant.now().minus(1, ChronoUnit.HOURS));
        seedSale(402, 100, 100, 5, false, Instant.now().minus(1, ChronoUnit.HOURS));
        seedListingsViewed(66, 100);
        refreshViews();
        // effective_profit floor -1 so the daily_sales=0 issue doesn't drop the row.
        offerFilters.upsert(
                1L, new OfferFilterRow(66, 1000, 50, 2.0, 24, -1.0, -1.0, -1.0, -1, -1, 1000, -1, "DATA_CENTER"));

        var offers = service.bestOffersFor(new BotUser(1L));
        assertEquals(1, offers.size());
        assertEquals(100, offers.getFirst().stats().item().id());
    }

    @Test
    void bestOffersForReturnsEmptyWhenHomeWorldMissing() {
        assertTrue(service.bestOffersFor(new BotUser(9999L)).isEmpty());
    }

    @Test
    void topForReturnsResultsForConfiguredWorld() {
        offerFilters.upsert(1L, homeFilter(66, "DATA_CENTER"));
        seedListing(66, 100, 500, 3, false);
        seedSale(66, 100, 500, 3, false, Instant.now().minus(1, ChronoUnit.HOURS));
        refreshViews();

        var top = service.topFor(
                new BotUser(1L),
                new TopFilter(SearchScope.WORLD, SortOrder.SALES, null, null, null, null, null, null, null));
        assertTrue(!top.isEmpty());
    }

    @Test
    void topForReturnsEmptyWhenHomeWorldMissing() {
        assertTrue(service.topFor(
                        new BotUser(9999L),
                        new TopFilter(SearchScope.WORLD, SortOrder.SALES, null, null, null, null, null, null, null))
                .isEmpty());
    }

    @Test
    void scopesEnumerationReturnsAllValues() {
        assertEquals(SearchScope.values().length, service.scopes().length);
    }
}
