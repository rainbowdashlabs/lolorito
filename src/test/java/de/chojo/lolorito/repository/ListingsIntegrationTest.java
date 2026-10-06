/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import de.chojo.universalis.entities.Item;
import de.chojo.universalis.entities.ItemMeta;
import de.chojo.universalis.entities.Listing;
import de.chojo.universalis.entities.Price;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ListingsIntegrationTest extends RepositoryTestBase {

    private static final World WORLD = Worlds.worldById(66); // Odin
    private static final World OTHER = Worlds.worldById(402); // Alpha (also Light)
    private static final Item ITEM = Item.ofId(1000);
    private Listings repo;

    private static Listing listing(int price, int qty) {
        return new Listing(
                Instant.now(),
                WORLD,
                null,
                new ItemMeta(false, false, 0, List.of()),
                "x",
                false,
                null,
                "seller",
                new Price(price, qty, price * qty),
                0);
    }

    @BeforeEach
    void setUp() {
        repo = new Listings();
        query("DELETE FROM listings").single(call()).delete();
        query("DELETE FROM listings_updated").single(call()).delete();
        query("DELETE FROM listings_viewed").single(call()).delete();
    }

    @Test
    void addListingsInsertsRowsAndStampsUpdatedAndViewed() {
        repo.addListings(ITEM, WORLD, List.of(listing(100, 1), listing(200, 3)));
        int listings = query("SELECT count(*) AS n FROM listings")
                .single(call())
                .map(row -> row.getInt("n"))
                .first()
                .orElseThrow();
        assertEquals(2, listings);
        int updated = query("SELECT count(*) AS n FROM listings_updated")
                .single(call())
                .map(row -> row.getInt("n"))
                .first()
                .orElseThrow();
        assertEquals(1, updated, "trigger should coalesce all listings for (item, world) into one updated row");
        int viewed = query("SELECT count(*) AS n FROM listings_viewed")
                .single(call())
                .map(row -> row.getInt("n"))
                .first()
                .orElseThrow();
        assertEquals(1, viewed, "listings_viewed carries a single row per (item, world, day)");
    }

    @Test
    void clearListingsRemovesRowsForThatWorldItemOnly() {
        repo.addListings(ITEM, WORLD, List.of(listing(100, 1)));
        repo.addListings(ITEM, OTHER, List.of(listing(200, 2)));
        repo.clearListings(ITEM, WORLD);
        int left = query("SELECT count(*) AS n FROM listings")
                .single(call())
                .map(row -> row.getInt("n"))
                .first()
                .orElseThrow();
        assertEquals(1, left);
        int leftWorld = query("SELECT world FROM listings")
                .single(call())
                .map(row -> row.getInt("world"))
                .first()
                .orElseThrow();
        assertEquals(OTHER.id(), leftWorld);
    }

    @Test
    void addListingsIncrementsViewedCountOnRepeat() {
        repo.addListings(ITEM, WORLD, List.of(listing(100, 1)));
        repo.addListings(ITEM, WORLD, List.of(listing(200, 1)));
        int count = query("SELECT count FROM listings_viewed WHERE world = :w AND item = :i")
                .single(call().bind("w", WORLD.id()).bind("i", ITEM.id()))
                .map(row -> row.getInt("count"))
                .first()
                .orElseThrow();
        assertEquals(2, count);
    }
}
