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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaleRateTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    // Effectively disables recency decay so the unweighted arithmetic in
    // these tests stays exact; decay behaviour has its own test below.
    private static final int HUGE_HALF_LIFE = 1_000_000;

    @Test
    void anchorAtMedianEqualsSalesPerHour() {
        List<SaleObservation> sales = new ArrayList<>();
        // 24 sales at the median (band [0.95, 1.05]) in the last 24 hours,
        // over a 1-day fit window → 1 sale/hour.
        for (int i = 0; i < 24; i++) {
            sales.add(new SaleObservation(1000, 1, NOW.minus(Duration.ofHours(i))));
        }
        SaleRate r = SaleRate.fit(sales, 1000.0, 1, HUGE_HALF_LIFE, NOW);
        assertEquals(1.0, r.lambdaP100(), 1e-3);
    }

    @Test
    void aggressiveUndercutSellsFaster() {
        List<SaleObservation> sales = new ArrayList<>();
        // 10 aggressive-undercut sales, 5 at-median, 2 above.
        for (int i = 0; i < 10; i++) {
            sales.add(new SaleObservation(950, 1, NOW.minus(Duration.ofHours(i))));
        }
        for (int i = 0; i < 5; i++) {
            sales.add(new SaleObservation(1000, 1, NOW.minus(Duration.ofHours(i))));
        }
        // Comfortably above BAND_MIN_WEIGHT so the p105 anchor keeps its own
        // rate rather than falling back — with recency decay the weighted
        // mass of N sales is always slightly below N.
        for (int i = 0; i < 4; i++) {
            sales.add(new SaleObservation(1050, 1, NOW.minus(Duration.ofHours(i))));
        }
        SaleRate r = SaleRate.fit(sales, 1000.0, 1, HUGE_HALF_LIFE, NOW);
        assertTrue(r.lambdaP95() > r.lambdaP100(), "Aggressive-undercut band should have the highest rate");
        assertTrue(r.lambdaP100() > r.lambdaP105(), "At-median rate should beat above-median rate");
    }

    @Test
    void interpolatedRateStrictlyBetweenAnchors() {
        SaleRate r = new SaleRate(4.0, 2.0, 1.0);
        double atMid = r.at(0.975); // halfway between 0.95 and 1.00
        assertTrue(
                atMid > 2.0 && atMid < 4.0,
                "Interpolated rate between anchors should sit strictly between them; got " + atMid);
    }

    @Test
    void interpolationClampsOutsideRange() {
        SaleRate r = new SaleRate(4.0, 2.0, 1.0);
        assertEquals(4.0, r.at(0.5), 1e-9);
        assertEquals(1.0, r.at(1.5), 1e-9);
    }

    @Test
    void emptyBandFallsBackToDeflatedOverallRate() {
        // 10 sales exclusively in the p100 band; p95 and p105 bands empty.
        List<SaleObservation> sales = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            sales.add(new SaleObservation(1000, 1, NOW.minus(Duration.ofHours(i))));
        }
        SaleRate r = SaleRate.fit(sales, 1000.0, 1, HUGE_HALF_LIFE, NOW);
        // Fallback = allRate × 0.6. p100 has 10 sales (≥ min-count) so it keeps its full rate.
        double allRate = 10.0 / 24.0;
        double fallback = allRate * 0.6;
        assertEquals(fallback, r.lambdaP95(), 1e-3);
        assertEquals(allRate, r.lambdaP100(), 1e-3);
        assertEquals(fallback, r.lambdaP105(), 1e-3);
    }

    @Test
    void sparseBandFallsBackRatherThanInflating() {
        // 20 sales at median, 2 sales at 0.95× (below the min-count gate).
        // The 2-sample p95 band should NOT report 2/24 = 0.083 sales/h; it
        // should use the deflated overall rate instead.
        List<SaleObservation> sales = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            sales.add(new SaleObservation(1000, 1, NOW.minus(Duration.ofHours(i))));
        }
        sales.add(new SaleObservation(950, 1, NOW.minus(Duration.ofHours(1))));
        sales.add(new SaleObservation(950, 1, NOW.minus(Duration.ofHours(2))));
        SaleRate r = SaleRate.fit(sales, 1000.0, 1, HUGE_HALF_LIFE, NOW);
        double allRate = 22.0 / 24.0;
        double fallback = allRate * 0.6;
        assertEquals(fallback, r.lambdaP95(), 1e-3);
    }

    @Test
    void recentSalesOutweighOldOnesWithShortHalfLife() {
        // Same total volume, but one key sold recently and the other a week
        // ago. With a 1-day half-life the recent seller must show a higher
        // at-median rate.
        List<SaleObservation> recent = new ArrayList<>();
        List<SaleObservation> stale = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            recent.add(new SaleObservation(1000, 1, NOW.minus(Duration.ofHours(i))));
            stale.add(new SaleObservation(1000, 1, NOW.minus(Duration.ofDays(7)).minus(Duration.ofHours(i))));
        }
        SaleRate fresh = SaleRate.fit(recent, 1000.0, 14, 1, NOW);
        SaleRate old = SaleRate.fit(stale, 1000.0, 14, 1, NOW);
        assertTrue(
                fresh.lambdaP100() > old.lambdaP100() * 10,
                "A week-old burst should carry a tiny fraction of a fresh burst's rate");
    }

    @Test
    void quantityCountsAsUnits() {
        // One 24-unit stack sale ≈ 24 single sales for throughput purposes.
        List<SaleObservation> stack = List.of(new SaleObservation(1000, 24, NOW.minus(Duration.ofHours(1))));
        List<SaleObservation> singles = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            singles.add(new SaleObservation(1000, 1, NOW.minus(Duration.ofHours(1))));
        }
        SaleRate a = SaleRate.fit(stack, 1000.0, 1, HUGE_HALF_LIFE, NOW);
        SaleRate b = SaleRate.fit(singles, 1000.0, 1, HUGE_HALF_LIFE, NOW);
        assertEquals(b.lambdaP100(), a.lambdaP100(), 1e-6);
    }
}
