/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.assertj.core.api.Assertions.assertThat;

class SalesTrendsIntegrationTest extends RepositoryTestBase {

    private static final int WORLD = 66;

    private SalesTrends repo;

    @BeforeEach
    void setUp() {
        repo = new SalesTrends(null);
        query("DELETE FROM sales").single(call()).delete();
    }

    /** Seed {@code units} sold {@code daysAgo} days back (mid-bucket so it can't straddle an edge). */
    private static void seedSale(int item, int units, double daysAgo) {
        Instant at = Instant.now().minus(Duration.ofMinutes((long) (daysAgo * 24 * 60) + 720));
        query("""
                INSERT INTO sales(world, item, hq, sold, unit_price, quantity, total)
                VALUES (:w, :i, false, :sold, 100, :q, :t)
                """)
                .single(call().bind("w", WORLD)
                        .bind("i", item)
                        .bind("sold", at, de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP)
                        .bind("q", units)
                        .bind("t", 100 * units))
                .insert();
    }

    @Test
    void risingSalesProducePositiveSlope() {
        // 1, 2, 4, 8, 16 units over five days, oldest first.
        int[] units = {1, 2, 4, 8, 16};
        for (int i = 0; i < units.length; i++) {
            seedSale(100, units[i], units.length - 1 - i);
        }
        var trends = repo.fit(WORLD, 5, 1);
        assertThat(trends).hasSize(1);
        var t = trends.get(0);
        assertThat(t.slope()).isPositive();
        assertThat(t.totalUnits()).isEqualTo(31);
        assertThat(t.lastDayUnits()).isEqualTo(16);
        // Regression forecast for the next day must sit above today's level
        // for an accelerating series.
        double predicted = t.intercept() + t.slope() * 5;
        assertThat(predicted).isGreaterThan(8);
    }

    @Test
    void fallingSalesProduceNegativeSlope() {
        int[] units = {20, 15, 10, 5, 1};
        for (int i = 0; i < units.length; i++) {
            seedSale(200, units[i], units.length - 1 - i);
        }
        var trends = repo.fit(WORLD, 5, 1);
        assertThat(trends).hasSize(1);
        assertThat(trends.get(0).slope()).isNegative();
        assertThat(trends.get(0).r2()).isGreaterThan(0.9);
    }

    @Test
    void quietDaysCountAsZeroesNotGaps() {
        // Sales only on the FIRST day of the window. Without zero-fill the
        // single bucket would fit a flat "trend"; with zero-fill the slope
        // must be clearly negative (item died).
        seedSale(300, 30, 6);
        var trends = repo.fit(WORLD, 7, 1);
        assertThat(trends).hasSize(1);
        assertThat(trends.get(0).slope()).isNegative();
        assertThat(trends.get(0).lastDayUnits()).isZero();
    }

    @Test
    void minUnitsGateDropsNoiseKeys() {
        seedSale(400, 2, 1);
        assertThat(repo.fit(WORLD, 7, 5)).isEmpty();
        assertThat(repo.fit(WORLD, 7, 1)).hasSize(1);
    }

    @Test
    void fitOneTargetsExactlyOneKey() {
        int[] units = {1, 2, 4, 8, 16};
        for (int i = 0; i < units.length; i++) {
            seedSale(100, units[i], units.length - 1 - i);
        }
        seedSale(999, 50, 1); // different item — must not leak in

        var one = repo.fitOne(WORLD, 100, false, 5).orElseThrow();
        assertThat(one.itemId()).isEqualTo(100);
        assertThat(one.totalUnits()).isEqualTo(31);
        assertThat(repo.fitOne(WORLD, 100, true, 5)).isEmpty();
        assertThat(repo.fitOne(WORLD, 12345, false, 5)).isEmpty();
    }

    @Test
    void otherWorldsSalesAreIgnored() {
        query("""
                INSERT INTO sales(world, item, hq, sold, unit_price, quantity, total)
                VALUES (402, 500, false, now(), 100, 10, 1000)
                """).single(call()).insert();
        assertThat(repo.fit(WORLD, 7, 1)).isEmpty();
    }
}
