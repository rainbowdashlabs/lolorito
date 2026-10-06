/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Recency-weighted log-normal fit over sale prices.
 * <ul>
 *   <li>{@code mu} and {@code sigma} live in log-space: {@code ln(price)} is
 *       approximately N(mu, sigma^2).</li>
 *   <li>{@code weightedN} is the sum of the exponential-decay weights of the
 *       samples that went into the fit — always ≥ 0, often below the raw
 *       count when many samples are old.</li>
 * </ul>
 *
 * <p>Read {@link #expectedPrice()} or {@link #medianPrice()} at the call
 * site; the log-space parameters are for onward math (variance propagation,
 * bootstrapping, etc.).
 */
public record PriceDistribution(double mu, double sigma, double weightedN) {

    /** Log-space distance beyond which a sample is treated as an outlier during the second-pass fit. */
    private static final double OUTLIER_SIGMAS = 3.0;

    /**
     * Fit from a batch of sales. Weight of each sale decays with age:
     * {@code w_i = exp(-Δt_i / τ) · sqrt(qty_i)}, where
     * {@code τ = halfLifeDays / ln 2} so a sale that is exactly
     * {@code halfLifeDays} old really carries half weight (plain
     * {@code τ = halfLifeDays} halves at ~0.69·halfLife — the old bug).
     *
     * <p>{@code sqrt(qty)} weights bulk sales above singles without letting
     * one 99-stack masquerade as 99 independent price observations — unit
     * prices within a stack sale are one draw from the market, not many.
     *
     * <p>Two-pass to shrug off obvious outliers (flip attempts, misprices,
     * botched decimal points): the first pass produces a rough {@code mu},
     * {@code sigma} on every sample; any log-price beyond {@link
     * #OUTLIER_SIGMAS}σ from that mean gets dropped and the second pass
     * re-fits on the survivors. When there are &lt; 3 samples we skip the
     * trim entirely — nothing to compare against.
     *
     * <p>Returns empty if there are no samples (a distribution with zero
     * weighted mass is meaningless). Callers combine this with a
     * min-samples check before persisting.
     */
    public static Optional<PriceDistribution> fit(List<SaleObservation> sales, double halfLifeDays, Instant now) {
        if (sales.isEmpty()) return Optional.empty();

        double tauSeconds = halfLifeDays * 86400.0 / Math.log(2.0);
        var first = weightedFit(sales, tauSeconds, now, Double.NaN, Double.NaN);
        if (first == null) return Optional.empty();

        // Second pass only helps when there is spread and enough samples.
        if (sales.size() < 3 || first.sigma <= 1e-9) return Optional.of(first);
        var second = weightedFit(sales, tauSeconds, now, first.mu, OUTLIER_SIGMAS * first.sigma);
        return Optional.of(second == null ? first : second);
    }

    /**
     * @param muCentre   if non-NaN, only samples with {@code |ln(price) - muCentre| <= muWindow}
     *                   contribute — the outlier trim.
     */
    private static PriceDistribution weightedFit(
            List<SaleObservation> sales, double tauSeconds, Instant now, double muCentre, double muWindow) {
        double weightedSum = 0.0;
        double weightedSumSq = 0.0;
        double totalWeight = 0.0;
        boolean trim = !Double.isNaN(muCentre) && !Double.isNaN(muWindow);

        for (SaleObservation s : sales) {
            if (s.unitPrice() <= 0) continue;
            double logPrice = Math.log(s.unitPrice());
            if (trim && Math.abs(logPrice - muCentre) > muWindow) continue;
            double ageSeconds = (now.toEpochMilli() - s.at().toEpochMilli()) / 1000.0;
            if (ageSeconds < 0) ageSeconds = 0;
            double weight = Math.exp(-ageSeconds / tauSeconds) * Math.sqrt(Math.max(1, s.quantity()));
            weightedSum += weight * logPrice;
            weightedSumSq += weight * logPrice * logPrice;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0) return null;
        double mu = weightedSum / totalWeight;
        double variance = Math.max(0.0, weightedSumSq / totalWeight - mu * mu);
        double sigma = Math.sqrt(variance);
        return new PriceDistribution(mu, sigma, totalWeight);
    }

    /**
     * Mean of the underlying log-normal: {@code E[price] = exp(mu + sigma^2/2)}.
     * This is what value math wants when it needs a scalar "expected price".
     */
    public double expectedPrice() {
        return Math.exp(mu + sigma * sigma / 2.0);
    }

    /**
     * Median of the underlying log-normal: {@code exp(mu)}. Used as the
     * price anchor for the sale-rate curve.
     */
    public double medianPrice() {
        return Math.exp(mu);
    }
}
