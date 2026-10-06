/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

/**
 * How far a valuation can be trusted, from the model's evidence and the
 * spread of the expected sale against the margin.
 *
 * <p>{@link #LOW} when the model is insufficient or one standard deviation
 * of the sell price wipes out the per-unit margin. {@link #HIGH} needs an
 * own-key fit (not pooled), at least {@link #HIGH_SAMPLE_FACTOR} times the
 * minimum sample count, and a spread of at most {@link #HIGH_SPREAD_RATIO}
 * of the margin. Everything else is {@link #MEDIUM}.
 */
public enum Confidence {
    HIGH,
    MEDIUM,
    LOW;

    static final int HIGH_SAMPLE_FACTOR = 2;
    static final double HIGH_SPREAD_RATIO = 0.5;

    /**
     * @param minSamples the fitter's minimum samples per key
     * @param buyPrice   per-unit cost of the offer
     */
    public static Confidence of(MarketModel model, Valuation valuation, int buyPrice, int minSamples) {
        if (!model.sufficient()) return LOW;
        double margin = valuation.expectedNet() - buyPrice;
        if (margin <= 0) return LOW;
        double spread = valuation.sigmaNet() / margin;
        if (spread > 1.0) return LOW;
        boolean wellSampled = model.sampleCount() >= HIGH_SAMPLE_FACTOR * minSamples;
        if (!model.pooled() && wellSampled && spread <= HIGH_SPREAD_RATIO) return HIGH;
        return MEDIUM;
    }
}
