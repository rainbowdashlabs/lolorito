/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.assertj.core.api.Assertions.assertThat;

class ItemDetailSalesHistoryTest extends RepositoryTestBase {

    private ItemDetail repo;

    @BeforeEach
    void setUp() {
        repo = new ItemDetail();
        query("DELETE FROM sales").single(call()).delete();
        insertWorld(66, "Odin", 7, "Light", "Europe");
    }

    @Test
    void emptyHistoryReturnsEmptyList() {
        assertThat(repo.salesHistory(1234, 66, false, 7)).isEmpty();
    }

    @Test
    void bucketsCollapseByDay() {
        seedSale(66, 5000, false, 100, 1, 1);
        seedSale(66, 5000, false, 200, 1, 1);
        seedSale(66, 5000, false, 150, 1, 2);
        var buckets = repo.salesHistory(5000, 66, false, 7);
        assertThat(buckets).hasSize(2);
        assertThat(buckets.stream().mapToInt(ItemDetail.SalesBucket::sales).sum())
                .isEqualTo(3);
    }

    @Test
    void hqFilterExcludesNq() {
        seedSale(66, 5001, false, 100, 1, 1);
        seedSale(66, 5001, true, 500, 1, 1);
        assertThat(repo.salesHistory(5001, 66, true, 7)).allSatisfy(b -> {
            assertThat(b.avgPrice()).isEqualTo(500);
        });
    }

    private static void seedSale(int world, int item, boolean hq, int price, int qty, int daysAgo) {
        query("""
                INSERT INTO sales(world, item, hq, sold, unit_price, quantity, total)
                VALUES (:w, :i, :hq, now() - (:days || ' days')::interval, :p, :q, :p)
                """)
                .single(call().bind("w", world)
                        .bind("i", item)
                        .bind("hq", hq)
                        .bind("p", price)
                        .bind("q", qty)
                        .bind("days", daysAgo))
                .insert();
    }
}
