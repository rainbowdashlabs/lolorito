/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Poisson sale-intensity at three price anchors — sales per hour when the
 * listing sits at 0.95×, 1.00×, and 1.05× the market median.
 *
 * <p>Bands are contiguous within [0.875, 1.125] so every near-median sale
 * feeds exactly one anchor; sales further out only feed the overall rate.
 * Band mass is recency-weighted with the same half-life as the price fit,
 * so a post-patch demand shift moves the anchors in days, not weeks.
 */
public record SaleRate(double lambdaP95, double lambdaP100, double lambdaP105) {

    // Contiguous band edges: [LOW_EDGE, MID_LOW) -> p95, [MID_LOW, MID_HIGH] -> p100,
    // (MID_HIGH, HIGH_EDGE] -> p105. Outside [LOW_EDGE, HIGH_EDGE] only allRate sees it.
    private static final double LOW_EDGE = 0.875;
    private static final double MID_LOW = 0.975;
    private static final double MID_HIGH = 1.025;
    private static final double HIGH_EDGE = 1.125;

    /**
     * A band must land at least this much weighted unit-mass to be trusted
     * as its own anchor. Quiet items would otherwise get an inflated
     * {@code λ(0.95)} whenever a single sale happened to fall inside the
     * aggressive band — the fallback to {@code allRate} × a small deflator
     * (below) is a lot closer to reality.
     */
    private static final double BAND_MIN_WEIGHT = 3.0;
    /**
     * Deflator applied to the overall rate when a band is empty or sparse.
     * The overall rate mixes fast and slow sales, so a listing at a
     * particular price ratio shouldn't inherit the full aggregate — a
     * conservative 60% factor keeps downstream EV/hour honest.
     */
    private static final double FALLBACK_DEFLATOR = 0.6;

    /**
     * Fit from the sales log. {@code halfLifeDays} applies the same
     * exponential recency decay as the price fit; rates are normalised by
     * the decayed window ({@code τ·(1 − e^(−W/τ))}) so a constant-rate
     * market yields the same λ as the unweighted estimate, while a
     * shifting market tracks the recent regime.
     */
    public static SaleRate fit(
            List<SaleObservation> sales, double medianPrice, int rateWindowDays, double halfLifeDays, Instant now) {
        if (medianPrice <= 0 || sales.isEmpty()) {
            return new SaleRate(0.0, 0.0, 0.0);
        }

        double tauSeconds = halfLifeDays * 86400.0 / Math.log(2.0);
        long cutoff = now.toEpochMilli() - Duration.ofDays(rateWindowDays).toMillis();
        double all = 0.0;
        double p95 = 0.0, p100 = 0.0, p105 = 0.0;

        for (SaleObservation s : sales) {
            if (s.at().toEpochMilli() < cutoff) continue;
            if (s.unitPrice() <= 0) continue;
            double ageSeconds = (now.toEpochMilli() - s.at().toEpochMilli()) / 1000.0;
            if (ageSeconds < 0) ageSeconds = 0;
            double weight = Math.exp(-ageSeconds / tauSeconds) * Math.max(1, s.quantity());
            all += weight;
            double ratio = s.unitPrice() / medianPrice;
            if (ratio < LOW_EDGE || ratio > HIGH_EDGE) continue;
            if (ratio < MID_LOW) p95 += weight;
            else if (ratio <= MID_HIGH) p100 += weight;
            else p105 += weight;
        }

        // Effective observation window of the decayed weights: integrating
        // exp(-t/τ) over [0, W] gives τ·(1 − e^(−W/τ)). Dividing weighted
        // mass by this keeps λ an unbiased sales-per-hour estimate when the
        // rate is constant.
        double windowSeconds = Duration.ofDays(rateWindowDays).toSeconds();
        double effectiveHours = tauSeconds * (1.0 - Math.exp(-windowSeconds / tauSeconds)) / 3600.0;
        if (effectiveHours <= 0) return new SaleRate(0.0, 0.0, 0.0);
        double allRate = all / effectiveHours;

        // Empty (or very sparse) band → fall back to a deflated overall
        // rate so downstream math doesn't multiply by a floor-zero AND
        // doesn't inherit the full aggregate for a niche band that just
        // happened to catch one sale.
        double fallback = allRate * FALLBACK_DEFLATOR;
        double r95 = p95 >= BAND_MIN_WEIGHT ? p95 / effectiveHours : fallback;
        double r100 = p100 >= BAND_MIN_WEIGHT ? p100 / effectiveHours : fallback;
        double r105 = p105 >= BAND_MIN_WEIGHT ? p105 / effectiveHours : fallback;
        return new SaleRate(r95, r100, r105);
    }

    /**
     * All three anchors scaled by {@code factor} — used to turn a DC-pooled
     * throughput into a per-world estimate (÷ world count).
     */
    public SaleRate scaled(double factor) {
        return new SaleRate(lambdaP95 * factor, lambdaP100 * factor, lambdaP105 * factor);
    }

    private static double logInterp(double x1, double y1, double x2, double y2, double x) {
        // Interpolate in log-space when both endpoints are strictly positive;
        // otherwise fall back on linear (log(0) is undefined).
        if (y1 > 0 && y2 > 0) {
            double logY = Math.log(y1) + (Math.log(y2) - Math.log(y1)) * (x - x1) / (x2 - x1);
            return Math.exp(logY);
        }
        return y1 + (y2 - y1) * (x - x1) / (x2 - x1);
    }

    /**
     * Log-linear interpolation over the three anchors — a lot cheaper than a
     * proper regression and still captures the "cheaper = faster" shape.
     * Values below 0.95 clamp to λ(0.95); values above 1.05 clamp to λ(1.05).
     */
    public double at(double priceRatio) {
        if (priceRatio <= 0.95) return lambdaP95;
        if (priceRatio >= 1.05) return lambdaP105;
        if (priceRatio <= 1.00) {
            return logInterp(0.95, lambdaP95, 1.00, lambdaP100, priceRatio);
        }
        return logInterp(1.00, lambdaP100, 1.05, lambdaP105, priceRatio);
    }
}
