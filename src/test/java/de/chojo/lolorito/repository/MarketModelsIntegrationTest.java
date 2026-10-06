/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.PriceDistribution;
import de.chojo.lolorito.value.SaleRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketModelsIntegrationTest extends RepositoryTestBase {

    private MarketModels repo;

    private static MarketModel model(int world, int item, boolean hq, boolean sufficient, Instant fitted) {
        return new MarketModel(
                item,
                world,
                hq,
                new PriceDistribution(6.9, 0.3, 42),
                new SaleRate(1.5, 1.0, 0.6),
                0.0,
                0.0,
                42,
                sufficient,
                false,
                fitted);
    }

    private static void seedSale(int world, int item, boolean hq, Instant when) {
        query("""
                INSERT INTO sales(world, item, hq, sold, unit_price, quantity, total)
                VALUES (:w, :i, :hq, :s, 100, 1, 100)
                """)
                .single(call().bind("w", world).bind("i", item).bind("hq", hq).bind("s", when, INSTANT_TIMESTAMP))
                .insert();
    }

    @BeforeEach
    void setUp() {
        repo = new MarketModels(dataSource);
        query("DELETE FROM market_model").single(call()).delete();
        query("DELETE FROM sales").single(call()).delete();
    }

    @Test
    void upsertInsertsFirstThenUpdatesInPlace() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repo.upsert(model(66, 100, false, true, now));
        var v1 = repo.find(66, 100, false).orElseThrow();
        assertEquals(6.9, v1.price().mu(), 1e-9);
        assertTrue(v1.sufficient());

        var later = new MarketModel(
                100,
                66,
                false,
                new PriceDistribution(7.0, 0.4, 100),
                new SaleRate(1.2, 0.8, 0.5),
                0.0,
                0.0,
                100,
                false,
                false,
                now.plus(1, ChronoUnit.HOURS));
        repo.upsert(later);
        var v2 = repo.find(66, 100, false).orElseThrow();
        assertEquals(7.0, v2.price().mu(), 1e-9);
        assertFalse(v2.sufficient());
        assertEquals(100, v2.sampleCount());
    }

    @Test
    void findRespectsHqFlag() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repo.upsert(model(66, 100, false, true, now));
        repo.upsert(model(66, 100, true, true, now));
        assertTrue(repo.find(66, 100, false).isPresent());
        assertTrue(repo.find(66, 100, true).isPresent());
    }

    @Test
    void findAbsentReturnsEmpty() {
        assertTrue(repo.find(66, 999, false).isEmpty());
    }

    @Test
    void refitCandidatesPicksKeysWithRecentSalesAndNoModel() {
        var now = Instant.now();
        seedSale(66, 200, false, now.minus(1, ChronoUnit.HOURS));
        seedSale(66, 200, false, now.minus(2, ChronoUnit.HOURS));
        seedSale(66, 300, true, now.minus(3, ChronoUnit.HOURS));

        var cutoff = now.minus(30, ChronoUnit.MINUTES);
        var candidates = repo.refitCandidates(cutoff, 30, 10);
        assertEquals(2, candidates.size(), "both keys with recent sales should be listed");
    }

    @Test
    void refitCandidatesSkipsKeysWithFreshModel() {
        var now = Instant.now();
        seedSale(66, 200, false, now.minus(1, ChronoUnit.HOURS));
        repo.upsert(model(66, 200, false, true, now));

        var candidates = repo.refitCandidates(now.minus(30, ChronoUnit.MINUTES), 30, 10);
        assertTrue(candidates.isEmpty(), "fitted-after-cutoff models are skipped");
    }

    @Test
    void refitCandidatesRespectsWindow() {
        var now = Instant.now();
        seedSale(66, 200, false, now.minus(60, ChronoUnit.DAYS));
        var candidates = repo.refitCandidates(now, 30, 10);
        assertTrue(candidates.isEmpty(), "sales outside the fit window don't trigger a refit");
    }
}
