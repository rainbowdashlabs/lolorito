/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Singleton;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.entities.Sale;
import de.chojo.universalis.worlds.World;

import java.util.Collection;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Raw SQL for the {@code sales} table. Universalis reports naked
 * {@link java.time.LocalDateTime} in UTC; we normalise to {@link
 * java.time.Instant} at the boundary so everything downstream stays UTC.
 */
@Singleton
public class Sales {
    public void addSales(World world, Item item, Collection<Sale> sales) {
        if (sales.isEmpty()) return;
        int worldId = world.id();
        int itemId = item.id();
        // One batched round trip per event — this is the hottest ingest
        // path. ON CONFLICT DO NOTHING against the natural key (patch_30):
        // websocket replays re-deliver sale batches, and every duplicate
        // row would inflate sale rates, weighted counts, and residuals.
        var calls = sales.stream()
                .map(sale -> call().bind("world", worldId)
                        .bind("item", itemId)
                        .bind("hq", sale.hq())
                        .bind("sold", sale.timestamp(), INSTANT_TIMESTAMP)
                        .bind("unit_price", sale.price().pricePerUnit())
                        .bind("quantity", sale.price().quantity())
                        .bind("total", sale.price().total()))
                .toList();
        query("""
                INSERT INTO sales(world, item, hq, sold, unit_price, quantity, total)
                VALUES (:world, :item, :hq, :sold, :unit_price, :quantity, :total)
                ON CONFLICT (world, item, hq, sold, unit_price, quantity) DO NOTHING
                """).batch(calls).insert();
    }

    public int clean() {
        return query("DELETE FROM sales WHERE sold < now() - '60 DAYS'::INTERVAL")
                .single(call())
                .delete()
                .rows();
    }
}
