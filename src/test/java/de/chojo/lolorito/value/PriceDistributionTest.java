/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceDistributionTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void emptyInputYieldsEmpty() {
        assertFalse(PriceDistribution.fit(List.of(), 7.0, NOW).isPresent());
    }

    @Test
    void tightClusterHasNearZeroSigmaAndExpectedNearMean() {
        List<SaleObservation> sales = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            sales.add(new SaleObservation(1000, 1, NOW.minus(Duration.ofHours(i))));
        }
        Optional<PriceDistribution> fit = PriceDistribution.fit(sales, 7.0, NOW);
        assertNotNull(fit.orElse(null));
        var d = fit.orElseThrow();
        assertEquals(0.0, d.sigma(), 1e-6, "Constant-price input must yield sigma ≈ 0 in log space");
        assertEquals(1000.0, d.medianPrice(), 1e-6);
        assertEquals(1000.0, d.expectedPrice(), 1e-6);
    }

    @Test
    void recencyWeightsFavourFreshSales() {
        List<SaleObservation> sales = new ArrayList<>();
        // Many stale sales at 500, a handful of fresh sales at 1500.
        // The recency weight should pull the median hard toward 1500.
        for (int i = 0; i < 50; i++) {
            sales.add(new SaleObservation(500, 1, NOW.minus(Duration.ofDays(30))));
        }
        for (int i = 0; i < 5; i++) {
            sales.add(new SaleObservation(1500, 1, NOW.minus(Duration.ofHours(i))));
        }
        var d = PriceDistribution.fit(sales, 3.0, NOW).orElseThrow();
        assertTrue(
                d.medianPrice() > 900, "Recency-weighted median should be pulled toward 1500, got " + d.medianPrice());
    }

    @Test
    void wideSpreadYieldsLargerSigma() {
        List<SaleObservation> tight = new ArrayList<>();
        List<SaleObservation> wide = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tight.add(new SaleObservation(1000 + (i % 2 == 0 ? 5 : -5), 1, NOW.minus(Duration.ofHours(i))));
            wide.add(new SaleObservation(i % 2 == 0 ? 500 : 2000, 1, NOW.minus(Duration.ofHours(i))));
        }
        var tightFit = PriceDistribution.fit(tight, 7.0, NOW).orElseThrow();
        var wideFit = PriceDistribution.fit(wide, 7.0, NOW).orElseThrow();
        assertTrue(wideFit.sigma() > tightFit.sigma() * 10, "Bimodal input must produce a materially larger sigma");
    }

    @Test
    void weightedNReflectsAgeDecay() {
        List<SaleObservation> ancient = List.of(
                new SaleObservation(1000, 1, NOW.minus(Duration.ofDays(30))),
                new SaleObservation(1000, 1, NOW.minus(Duration.ofDays(30))));
        List<SaleObservation> fresh = List.of(new SaleObservation(1000, 1, NOW), new SaleObservation(1000, 1, NOW));
        double freshN = PriceDistribution.fit(fresh, 7.0, NOW).orElseThrow().weightedN();
        double ancientN = PriceDistribution.fit(ancient, 7.0, NOW).orElseThrow().weightedN();
        assertTrue(freshN > ancientN * 10, "Fresh weighted-N should dominate ancient (both are the same raw count)");
    }

    @Test
    void extremeOutliersAreTrimmedInSecondPass() {
        // Ten sales all around 100 gil plus one flip-attempt at 100_000. The
        // trim should push mu close to ln(100) rather than the naive log-mean.
        var sales = new ArrayList<SaleObservation>();
        for (int i = 0; i < 10; i++) sales.add(new SaleObservation(100, 1, NOW.minusSeconds(i * 60L)));
        sales.add(new SaleObservation(100_000, 1, NOW.minusSeconds(30L)));

        var trimmed = PriceDistribution.fit(sales, 7.0, NOW).orElseThrow();
        double naiveMu = Math.log(100.0);
        assertTrue(
                Math.abs(trimmed.mu() - naiveMu) < 0.05,
                "Trimmed mu should sit near the cluster centre despite the outlier, got " + trimmed.mu());
    }

    @Test
    void trimSkippedWhenBelowThreeSamples() {
        // Two samples — trim is disabled, so the outlier still dominates.
        var sales = List.of(new SaleObservation(100, 1, NOW), new SaleObservation(10_000, 1, NOW));
        var out = PriceDistribution.fit(sales, 7.0, NOW).orElseThrow();
        // Naive log-mean of ln(100) and ln(10 000) is (ln 100 + ln 10 000) / 2 = ln(1000).
        assertTrue(Math.abs(out.mu() - Math.log(1000.0)) < 0.05);
    }
}
