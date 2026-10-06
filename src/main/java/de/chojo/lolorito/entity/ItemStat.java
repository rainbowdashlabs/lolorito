/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import de.chojo.universalis.entities.Item;
import de.chojo.universalis.worlds.World;

import java.time.Instant;

/**
 * Pre-aggregated stats for one {@code (world, item, hq)}. Consumed by both
 * the offer explorer (Discord) and the /top listing. World may be {@code
 * null} for cross-world aggregates.
 */
public record ItemStat(
        World world,
        Item item,
        boolean hq,
        Instant updated,
        double marketVolume,
        double interest,
        double popularity,
        int sales,
        int views,
        int minPrice,
        int avgPrice,
        int listings,
        int minSales,
        int maxSales,
        int avgSales) {

    /**
     * Empty-stats sentinel when no row is available for a key.
     */
    public static ItemStat empty(World world, Item item, Boolean hq) {
        return new ItemStat(world, item, hq != null && hq, Instant.now(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public String prettyText() {
        return """
                ```
                High Quality:  %s
                Market Volume: %.02f%%
                Interest:      %.02f%%
                Popularity:    %.02f%%
                Sales:         %,d
                Views:         %,d
                Min Price:     %,d
                Average Price: %,d
                Min Sold:      %,d
                Average Sold:  %,d
                Max Sold:      %,d
                Listings:      %,d
                ```
                """.formatted(
                hq(),
                marketVolume(),
                interest(),
                popularity(),
                sales(),
                views(),
                minPrice(),
                avgPrice(),
                minSales(),
                avgSales(),
                maxSales(),
                listings()).stripIndent();
    }

    public String universalisUrl() {
        return "https://universalis.app/market/%d".formatted(item().id());
    }
}
