/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketModelResidualsIntegrationTest extends RepositoryTestBase {

    private MarketModelResiduals repo;

    @BeforeEach
    void setUp() {
        repo = new MarketModelResiduals(dataSource);
        query("DELETE FROM market_model_residuals").single(call()).delete();
    }

    @Test
    void insertAndAggregateWindowedMean() {
        var now = Instant.now();
        repo.insert(100, 66, false, now.minus(1, ChronoUnit.HOURS), 0.1);
        repo.insert(100, 66, false, now.minus(2, ChronoUnit.HOURS), 0.2);
        repo.insert(100, 66, false, now.minus(3, ChronoUnit.HOURS), 0.3);
        var summary = repo.windowedSummary(100, 66, false, 7).orElseThrow();
        assertEquals(3, summary.count());
        assertEquals(0.2, summary.mean(), 1e-9);
        assertTrue(summary.sigma() > 0);
    }

    @Test
    void windowedSummaryHonoursCutoff() {
        var now = Instant.now();
        repo.insert(100, 66, false, now.minus(1, ChronoUnit.HOURS), 0.1);
        repo.insert(100, 66, false, now.minus(30, ChronoUnit.DAYS), 5.0);
        var summary = repo.windowedSummary(100, 66, false, 7).orElseThrow();
        assertEquals(1, summary.count(), "row outside the 7-day window drops out");
        assertEquals(0.1, summary.mean(), 1e-9);
    }

    @Test
    void windowedSummaryEmptyWhenNoRows() {
        assertTrue(repo.windowedSummary(999, 66, false, 7).isEmpty());
    }

    @Test
    void windowedSummaryIgnoresOtherKeys() {
        var now = Instant.now();
        repo.insert(100, 66, false, now, 0.5);
        repo.insert(101, 66, false, now, 100.0); // different item
        repo.insert(100, 67, false, now, 100.0); // different world
        repo.insert(100, 66, true, now, 100.0); // different hq
        var summary = repo.windowedSummary(100, 66, false, 7).orElseThrow();
        assertEquals(1, summary.count());
        assertEquals(0.5, summary.mean(), 1e-9);
    }

    @Test
    void globalSummaryCoversAllKeysInWindow() {
        var now = Instant.now();
        repo.insert(100, 66, false, now, 0.2);
        repo.insert(101, 67, true, now, -0.1);
        var summary = repo.globalSummary(7);
        assertEquals(2, summary.count());
        assertEquals(0.05, summary.mean(), 1e-9);
    }

    @Test
    void globalSummaryEmptyReturnsZeroes() {
        var summary = repo.globalSummary(7);
        assertEquals(0, summary.count());
        assertEquals(0.0, summary.mean(), 1e-9);
        assertEquals(0.0, summary.sigma(), 1e-9);
    }

    @Test
    void worstKeysRanksByAbsoluteBiasAndGatesOnCount() {
        var now = Instant.now();
        // Key A: 3 residuals, mild bias 0.1.
        repo.insert(100, 66, false, now, 0.1);
        repo.insert(100, 66, false, now, 0.1);
        repo.insert(100, 66, false, now, 0.1);
        // Key B: 3 residuals, strong negative bias −0.5 → must rank first.
        repo.insert(101, 66, false, now, -0.5);
        repo.insert(101, 66, false, now, -0.5);
        repo.insert(101, 66, false, now, -0.5);
        // Key C: single huge outlier — below min count, must not appear.
        repo.insert(102, 66, false, now, 3.0);

        var worst = repo.worstKeys(7, 3, 10);
        assertEquals(2, worst.size(), "The single-observation key must be gated out");
        assertEquals(101, worst.get(0).itemId(), "Largest absolute mean bias ranks first");
        assertEquals(-0.5, worst.get(0).mean(), 1e-9);
        assertEquals(100, worst.get(1).itemId());
    }
}
