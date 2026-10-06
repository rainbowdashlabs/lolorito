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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfMetricsIntegrationTest extends RepositoryTestBase {

    private PerfMetrics repo;

    @BeforeEach
    void setUp() {
        repo = new PerfMetrics();
        query("DELETE FROM perf_metric").single(call()).delete();
    }

    @Test
    void recordAndReadRoundTrip() {
        repo.record("model_refit_ms", 125L, "10 keys / 8 fitted");
        repo.record("model_refit_ms", 500L, "40 keys / 30 fitted");
        var rows = repo.recent("model_refit_ms", 10);
        assertEquals(2, rows.size());
        assertEquals(500L, rows.getFirst().valueMs(), "newest first");
    }

    @Test
    void recentIgnoresOtherMetrics() {
        repo.record("model_refit_ms", 100L, null);
        repo.record("view_refresh_total_ms", 200L, null);
        assertEquals(1, repo.recent("model_refit_ms", 10).size());
        assertEquals(1, repo.recent("view_refresh_total_ms", 10).size());
    }

    @Test
    void recentClampsLimitAtLeastOne() {
        repo.record("m", 1L, null);
        assertEquals(1, repo.recent("m", 0).size(), "limit=0 → clamps to 1");
    }

    @Test
    void databaseSizeBytesReturnsPositiveNumber() {
        assertTrue(repo.databaseSizeBytes() > 0);
    }
}
