/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import de.chojo.lolorito.service.ListingDiffer;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.entities.ItemMeta;
import de.chojo.universalis.entities.Listing;
import de.chojo.universalis.entities.Price;
import de.chojo.universalis.entities.Retainer;
import de.chojo.universalis.entities.Sale;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifecycle of {@code listing_episode} + {@code undercut_event} as driven
 * by {@link ListingDiffer}: Universalis sends full snapshots, the differ
 * turns them into posted / still-there / gone transitions.
 */
class ListingEpisodesIntegrationTest extends RepositoryTestBase {

    private static final World WORLD = Worlds.worldById(66); // Odin (Light)
    private static final Item ITEM = Item.ofId(4242);

    private ListingEpisodes episodes;
    private ListingDiffer differ;
    private Sales sales;

    private static Listing listing(String retainerId, int unitPrice, int qty, boolean hq) {
        return new Listing(
                Instant.now(),
                WORLD,
                null,
                new ItemMeta(hq, false, 0, List.of()),
                null, // listingId — almost always null upstream, force the synthetic identity path
                false,
                new Retainer(retainerId, "retainer-" + retainerId, null),
                null,
                new Price(unitPrice, qty, unitPrice * qty),
                0);
    }

    @BeforeEach
    void setUp() {
        episodes = new ListingEpisodes(dataSource);
        differ = new ListingDiffer(episodes);
        sales = new Sales();
        query("DELETE FROM listing_episode").single(call()).delete();
        query("DELETE FROM undercut_event").single(call()).delete();
        query("DELETE FROM sales").single(call()).delete();
    }

    @Test
    void firstSnapshotOpensEpisodes() {
        differ.onSnapshot(
                WORLD, ITEM, List.of(listing("r1", 1000, 1, false), listing("r2", 1200, 3, false)), Instant.now());
        var open = episodes.openFor(WORLD.id(), ITEM.id());
        assertEquals(2, open.size());
    }

    @Test
    void survivingListingIsTouchedNotDuplicated() {
        var now = Instant.now();
        differ.onSnapshot(WORLD, ITEM, List.of(listing("r1", 1000, 1, false)), now.minus(Duration.ofMinutes(10)));
        differ.onSnapshot(WORLD, ITEM, List.of(listing("r1", 1000, 1, false)), now);
        var open = episodes.openFor(WORLD.id(), ITEM.id());
        assertEquals(1, open.size(), "Same identity across snapshots must stay one episode");
    }

    @Test
    void vanishedListingWithMatchingSaleClosesAsSold() {
        var posted = Instant.now().minus(Duration.ofHours(2));
        differ.onSnapshot(WORLD, ITEM, List.of(listing("r1", 1000, 2, false)), posted);
        // The sale arrives before the next snapshot.
        sales.addSales(
                WORLD, ITEM, List.of(new Sale(false, new Price(1000, 2, 2000), Instant.now(), false, WORLD, null)));
        differ.onSnapshot(WORLD, ITEM, List.of(), Instant.now());

        assertEquals(0, episodes.openFor(WORLD.id(), ITEM.id()).size());
        var sold = episodes.recentSold(1, 10);
        assertEquals(1, sold.size());
        assertEquals(1000, sold.get(0).unitPrice());
    }

    @Test
    void vanishedListingWithoutSaleClosesAsDelistedThenHealsOnLateSale() {
        var posted = Instant.now().minus(Duration.ofHours(2));
        differ.onSnapshot(WORLD, ITEM, List.of(listing("r1", 1500, 1, false)), posted);
        differ.onSnapshot(WORLD, ITEM, List.of(), Instant.now());
        assertEquals(0, episodes.recentSold(1, 10).size(), "No matching sale → DELISTED");

        // The sale event arrives late; the differ reclassifies.
        differ.onSales(
                WORLD,
                ITEM.id(),
                List.of(new Sale(false, new Price(1500, 1, 1500), Instant.now(), false, WORLD, null)));
        assertEquals(1, episodes.recentSold(1, 10).size(), "Late sale must heal DELISTED → SOLD");
    }

    @Test
    void newFloorBelowOldFloorRecordsUndercut() {
        var now = Instant.now();
        differ.onSnapshot(WORLD, ITEM, List.of(listing("r1", 1000, 1, false)), now.minus(Duration.ofMinutes(10)));
        differ.onSnapshot(WORLD, ITEM, List.of(listing("r1", 1000, 1, false), listing("r2", 900, 1, false)), now);
        double perHour = episodes.undercutsPerHour(WORLD.id(), ITEM.id(), false, 1);
        assertTrue(perHour > 0, "A new listing below the floor must record an undercut event");
    }

    @Test
    void identicalStacksFromSameRetainerStayDistinctEpisodes() {
        differ.onSnapshot(
                WORLD, ITEM, List.of(listing("r1", 1000, 1, false), listing("r1", 1000, 1, false)), Instant.now());
        assertEquals(2, episodes.openFor(WORLD.id(), ITEM.id()).size());
    }

    @Test
    void ghostFractionCountsAgedOpenUnits() {
        var old = Instant.now().minus(Duration.ofDays(30));
        differ.onSnapshot(WORLD, ITEM, List.of(listing("r1", 1000, 1, false)), old);
        // Still listed today, plus a fresh one.
        differ.onSnapshot(
                WORLD, ITEM, List.of(listing("r1", 1000, 1, false), listing("r2", 1100, 1, false)), Instant.now());
        double ghost = episodes.ghostFraction(WORLD.id(), ITEM.id(), false, 14);
        assertEquals(0.5, ghost, 1e-9, "One of two open units sat past the ghost window");
    }

    @Test
    void cleanDropsOnlyEndedEpisodesPastRetention() {
        var ancient = Instant.now().minus(Duration.ofDays(90));
        differ.onSnapshot(WORLD, ITEM, List.of(listing("r1", 1000, 1, false)), ancient);
        differ.onSnapshot(WORLD, ITEM, List.of(), ancient.plus(Duration.ofHours(1)));
        differ.onSnapshot(WORLD, ITEM, List.of(listing("r2", 1000, 1, false)), Instant.now());

        int removed = episodes.clean(60);
        assertEquals(1, removed);
        assertEquals(1, episodes.openFor(WORLD.id(), ITEM.id()).size(), "Open episodes never expire via clean");
    }
}
