/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.repository.MarketModelResiduals;
import de.chojo.lolorito.repository.PerfMetrics;
import de.chojo.lolorito.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationHistoryIntegrationTest extends RepositoryTestBase {

    private CalibrationService svc;

    @BeforeEach
    void setUp() {
        query("DELETE FROM calibration_snapshot").single(call()).delete();
        query("DELETE FROM market_model").single(call()).delete();
        query("DELETE FROM perf_metric").single(call()).delete();
        svc = new CalibrationService(
                new MarketModelResiduals(null),
                new PerfMetrics(),
                new de.chojo.lolorito.repository.ListingEpisodes(null),
                new de.chojo.lolorito.repository.MarketModels(null),
                de.chojo.universalis.provider.NameSupplier.EMPTY);
    }

    @Test
    void historyReturnsRowsInsideWindowOnly() {
        var recent = Instant.now();
        seedSnapshot(recent, 100, 0.05, 0.20);
        seedSnapshot(recent.minus(2, ChronoUnit.DAYS), 200, 0.03, 0.18);
        seedSnapshot(recent.minus(30, ChronoUnit.DAYS), 300, 0.01, 0.15);
        var out = svc.history(24 * 7);
        assertEquals(2, out.size(), "only rows inside the 7-day window are returned");
        assertTrue(out.getFirst().sampleCount() > 0);
    }

    @Test
    void historyDefaultsHoursOnZero() {
        var recent = Instant.now();
        seedSnapshot(recent, 100, 0.05, 0.20);
        assertEquals(1, svc.history(0).size());
    }

    @Test
    void capabilityStatsAggregatesCurrentDb() {
        seedMarketModel(66, 100, true, true);
        seedMarketModel(66, 101, false, true);
        seedMarketModel(97, 100, true, false);
        // Perf metric row so the PerfPoint accessors are covered.
        new PerfMetrics().record("model_refit_ms", 42L, "sample");
        var stats = svc.capabilityStats();
        assertEquals(3, stats.totalModels());
        assertEquals(2, stats.sufficientModels(), "one row below threshold");
        assertEquals(2, stats.uniqueItems());
        assertEquals(2, stats.uniqueWorlds());
        assertTrue(stats.databaseBytes() > 0);
        assertNotNull(stats.modelRefitMs());
        assertNotNull(stats.viewRefreshMs());
        var point = stats.modelRefitMs().getFirst();
        assertEquals(42L, point.valueMs());
        assertEquals("sample", point.detail());
        assertNotNull(point.capturedAt());
    }

    private static void seedSnapshot(Instant t, int count, double mean, double sigma) {
        query("""
                INSERT INTO calibration_snapshot
                    (captured_at, window_days, sample_count, log_ratio_mean, log_ratio_sigma)
                VALUES (:t, 14, :n, :m, :s)
                ON CONFLICT (captured_at) DO NOTHING
                """)
                .single(call().bind("t", t, INSTANT_TIMESTAMP)
                        .bind("n", count)
                        .bind("m", mean)
                        .bind("s", sigma))
                .insert();
    }

    private static void seedMarketModel(int worldId, int itemId, boolean hq, boolean sufficient) {
        query("""
                INSERT INTO market_model
                    (item_id, world_id, hq, price_mu, price_sigma, sample_count, lambda_p95, lambda_p100, lambda_p105,
                     lambda_undercut, ghost_fraction, sufficient, fitted_at)
                VALUES (:i, :w, :hq, 6.0, 0.2, 30, 0.4, 0.6, 0.2, 0.0, 0.0, :ok, :t)
                """)
                .single(call().bind("i", itemId)
                        .bind("w", worldId)
                        .bind("hq", hq)
                        .bind("ok", sufficient)
                        .bind("t", Instant.now(), INSTANT_TIMESTAMP))
                .insert();
    }
}
