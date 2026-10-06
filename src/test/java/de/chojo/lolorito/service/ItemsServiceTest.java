/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.entity.BotUser;
import de.chojo.lolorito.entity.ItemListing;
import de.chojo.lolorito.entity.ItemStat;
import de.chojo.lolorito.entity.OfferFilterRow;
import de.chojo.lolorito.entity.SearchScope;
import de.chojo.lolorito.entity.SortOrder;
import de.chojo.lolorito.entity.TopFilter;
import de.chojo.lolorito.repository.Items;
import de.chojo.lolorito.repository.OfferFilters;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.worlds.World;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemsServiceTest {

    @Test
    void topForReturnsEmptyWhenNoHomeWorldSet() {
        // Default filter world id is -1 → Worlds.worldById(-1) returns null → empty.
        var filters = new FilterService(new StubOfferFilters(Map.of()));
        var service = new ItemsService(null, filters, null);
        var out = service.topFor(
                new BotUser(42),
                new TopFilter(SearchScope.WORLD, SortOrder.MARKET_VOLUME, null, null, null, null, null, null, null));
        assertTrue(out.isEmpty(), "no home world → no results, even before we touch the Items repo");
    }

    @Test
    void bestOffersForReturnsEmptyWhenNoHomeWorldSet() {
        var filters = new FilterService(new StubOfferFilters(Map.of()));
        var service = new ItemsService(null, filters, null);
        assertTrue(service.bestOffersFor(new BotUser(42)).isEmpty());
    }

    @Test
    void listingForReturnsEmptyOffersWhenNoHomeWorldSet() {
        var filters = new FilterService(new StubOfferFilters(Map.of()));
        var service = new ItemsService(new StubItems(), filters, null);
        var listing = service.listingFor(new BotUser(42), 100, false);
        assertTrue(listing.offers().isEmpty(), "no home world → no per-world listings assembled");
    }

    // --- Stubs ------------------------------------------------------------

    private static final class StubOfferFilters extends OfferFilters {
        private final Map<Long, OfferFilterRow> rows;

        StubOfferFilters(Map<Long, OfferFilterRow> rows) {
            super(null);
            this.rows = new HashMap<>(rows);
        }

        @Override
        public Optional<OfferFilterRow> find(long userId) {
            return Optional.ofNullable(rows.get(userId));
        }

        @Override
        public void upsert(long userId, OfferFilterRow filter) {
            rows.put(userId, filter);
        }
    }

    private static final class StubItems extends Items {
        StubItems() {
            super(null);
        }

        @Override
        public List<ItemListing> currentListings(int itemId, int dataCenterId, Boolean hq) {
            return List.of();
        }

        @Override
        public ItemStat stats(World world, Item item, Boolean hq) {
            return ItemStat.empty(world, item, hq);
        }
    }
}
