/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Singleton;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.entities.Listing;
import de.chojo.universalis.worlds.World;
import org.slf4j.Logger;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Raw SQL for the {@code listings} + {@code listings_viewed} tables.
 */
@Singleton
public class Listings {
    private static final Logger log = getLogger(Listings.class);

    public void addListings(Item item, World world, List<Listing> listings) {
        int worldId = world.id();
        int itemId = item.id();

        for (Listing listing : listings) {
            var reviewedAt = listing.lastReviewTime();
            var retainer = listing.retainer();
            query("""
                    INSERT INTO listings(world, item, hq, review_time, unit_price, quantity, total,
                                         retainer_id, retainer_name)
                    VALUES (:world, :item, :hq, :review, :unit_price, :quantity, :total,
                            :retainer_id, :retainer_name)
                    """)
                    .single(call().bind("world", worldId)
                            .bind("item", itemId)
                            .bind("hq", listing.meta().hq())
                            .bind("review", reviewedAt, INSTANT_TIMESTAMP)
                            .bind("unit_price", listing.price().pricePerUnit())
                            .bind("quantity", listing.price().quantity())
                            .bind("total", listing.price().total())
                            .bind("retainer_id", retainer == null ? null : retainer.id())
                            .bind("retainer_name", retainer == null ? null : retainer.name()))
                    .insert();
        }

        query("""
                INSERT INTO listings_viewed AS l (world, item)
                VALUES (:world, :item)
                ON CONFLICT(world, item, day)
                    DO UPDATE SET count = l.count + 1
                """).single(call().bind("world", worldId).bind("item", itemId)).insert();
    }

    public void clearListings(Item item, World world) {
        query("DELETE FROM listings WHERE item = :item AND world = :world")
                .single(call().bind("item", item.id()).bind("world", world.id()))
                .delete();
    }
}
