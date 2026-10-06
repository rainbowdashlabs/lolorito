/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import de.chojo.universalis.entities.Item;
import de.chojo.universalis.entities.Price;
import de.chojo.universalis.entities.Sale;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SalesIntegrationTest extends RepositoryTestBase {

    private static final World WORLD = Worlds.worldById(66); // Odin (Light)
    private static final Item ITEM = Item.ofId(1234);
    private Sales repo;

    private static Sale sale(int unit, int qty, Instant when) {
        return new Sale(false, new Price(unit, qty, unit * qty), when, false, WORLD, null);
    }

    @BeforeEach
    void setUp() {
        repo = new Sales();
        query("DELETE FROM sales").single(call()).delete();
    }

    @Test
    void addSalesInsertsEveryRow() {
        var sales = List.of(
                sale(100, 1, Instant.now().minus(java.time.Duration.ofHours(2))),
                sale(200, 3, Instant.now().minus(java.time.Duration.ofHours(1))));
        repo.addSales(WORLD, ITEM, sales);
        int count = query("SELECT count(*) AS n FROM sales")
                .single(call())
                .map(row -> row.getInt("n"))
                .first()
                .orElseThrow();
        assertEquals(2, count);
    }

    @Test
    void cleanDropsRowsOlderThanSixtyDays() {
        var fresh = sale(100, 1, Instant.now().minus(java.time.Duration.ofDays(5)));
        var stale = sale(200, 2, Instant.now().minus(java.time.Duration.ofDays(90)));
        repo.addSales(WORLD, ITEM, List.of(fresh, stale));

        int deleted = repo.clean();
        assertEquals(1, deleted);
        int remaining = query("SELECT count(*) AS n FROM sales")
                .single(call())
                .map(row -> row.getInt("n"))
                .first()
                .orElseThrow();
        assertEquals(1, remaining);
    }

    @Test
    void cleanIsIdempotentWhenNothingStale() {
        repo.addSales(WORLD, ITEM, List.of(sale(100, 1, Instant.now())));
        assertEquals(0, repo.clean());
    }
}
