/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfidenceTest {
    private static final int MIN_SAMPLES = 5;
    private static final int BUY = 1_000;

    private static MarketModel model(int samples, boolean sufficient, boolean pooled) {
        var price = new PriceDistribution(8.0, 0.1, samples);
        var rate = new SaleRate(1.0, 1.0, 1.0);
        return new MarketModel(1, 66, false, price, rate, 0.0, 0.0, samples, sufficient, pooled, Instant.now());
    }

    private static Valuation valuation(double expectedNet, double sigmaNet) {
        return new Valuation(expectedNet, sigmaNet, 0.0, 1.0, 0.0, 1.0);
    }

    @Test
    void wellSampledOwnFitWithTightSpreadIsHigh() {
        assertEquals(Confidence.HIGH, Confidence.of(model(10, true, false), valuation(2_000, 400), BUY, MIN_SAMPLES));
    }

    @Test
    void pooledFitIsAtMostMedium() {
        assertEquals(Confidence.MEDIUM, Confidence.of(model(50, true, true), valuation(2_000, 100), BUY, MIN_SAMPLES));
    }

    @Test
    void thinSampleIsAtMostMedium() {
        assertEquals(Confidence.MEDIUM, Confidence.of(model(9, true, false), valuation(2_000, 100), BUY, MIN_SAMPLES));
    }

    @Test
    void wideSpreadIsMedium() {
        assertEquals(Confidence.MEDIUM, Confidence.of(model(50, true, false), valuation(2_000, 800), BUY, MIN_SAMPLES));
    }

    @Test
    void spreadBeyondTheMarginIsLow() {
        assertEquals(Confidence.LOW, Confidence.of(model(50, true, false), valuation(2_000, 1_200), BUY, MIN_SAMPLES));
    }

    @Test
    void insufficientModelIsLow() {
        assertEquals(Confidence.LOW, Confidence.of(model(50, false, false), valuation(2_000, 10), BUY, MIN_SAMPLES));
    }

    @Test
    void noMarginIsLow() {
        assertEquals(Confidence.LOW, Confidence.of(model(50, true, false), valuation(900, 10), BUY, MIN_SAMPLES));
    }
}
