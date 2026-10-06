/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.repository.MarketModelResiduals;
import de.chojo.lolorito.repository.PerfMetrics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationServiceTest {

    @Test
    void interpretsZeroObservationsAsNoData() {
        String out = CalibrationService.interpret(0, 0.0);
        assertTrue(out.toLowerCase().contains("no sales"), () -> out);
    }

    @Test
    void interpretsSmallBiasAsBalanced() {
        String out = CalibrationService.interpret(100, 0.01);
        assertTrue(out.contains("within"), () -> out);
    }

    @Test
    void interpretsPositiveBiasAsAbovePredictions() {
        String out = CalibrationService.interpret(100, Math.log(1.15));
        assertTrue(out.contains("above"), () -> out);
        assertTrue(out.contains("15"), () -> out);
    }

    @Test
    void interpretsNegativeBiasAsBelowPredictions() {
        String out = CalibrationService.interpret(100, Math.log(0.90));
        assertTrue(out.contains("below"), () -> out);
        assertTrue(out.contains("10"), () -> out);
    }

    @Test
    void recentDelegatesToLast14Days() {
        var stub = new StubResiduals(new MarketModelResiduals.Summary(42, 0.05, 0.2));
        var service = new CalibrationService(
                stub,
                new PerfMetrics(),
                new de.chojo.lolorito.repository.ListingEpisodes(null),
                new de.chojo.lolorito.repository.MarketModels(null),
                de.chojo.universalis.provider.NameSupplier.EMPTY);
        var snap = service.recent();
        assertEquals(14, snap.windowDays());
        assertEquals(42, snap.sampleCount());
        assertEquals(0.05, snap.logRatioMean(), 1e-9);
    }

    @Test
    void lastNDaysClampsWindowToPositive() {
        var stub = new StubResiduals(new MarketModelResiduals.Summary(0, 0.0, 0.0));
        var service = new CalibrationService(
                stub,
                new PerfMetrics(),
                new de.chojo.lolorito.repository.ListingEpisodes(null),
                new de.chojo.lolorito.repository.MarketModels(null),
                de.chojo.universalis.provider.NameSupplier.EMPTY);
        assertEquals(1, service.lastNDays(0).windowDays());
        assertEquals(1, service.lastNDays(-5).windowDays());
    }

    private static final class StubResiduals extends MarketModelResiduals {
        private final Summary summary;

        StubResiduals(Summary summary) {
            super(null);
            this.summary = summary;
        }

        @Override
        public GlobalSummary globalSummary(int windowDays) {
            return new GlobalSummary(summary.count(), summary.mean(), summary.sigma(), summary.mean());
        }

        @Override
        public Optional<Summary> windowedSummary(int itemId, int worldId, boolean hq, int windowDays) {
            return Optional.of(summary);
        }

        @Override
        public void insert(int itemId, int worldId, boolean hq, Instant observedAt, double logRatio) {
            // no-op
        }
    }
}
