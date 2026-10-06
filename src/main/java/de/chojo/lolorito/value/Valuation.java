/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

/**
 * The engine's output. Every downstream consumer sorts / thresholds on
 * {@link #evPerHour()} — the attention-adjusted objective.
 *
 * <p>{@link #sigmaNet()} is the honest one-sigma spread on {@code evNet}
 * given the fitted price distribution; UI code colours large sigmas as
 * "risky" rather than pretending the point estimate is precise.
 *
 * @param expectedNet              gil per unit after tax at the chosen list
 *                                 price (undercut-race adjusted)
 * @param sigmaNet                 one-sigma spread on {@code expectedNet}
 *                                 (log-normal std, converted to gil space)
 * @param evGross                  total expected profit before attention
 *                                 cost, {@code qty × (expectedNet − buyPrice)}
 * @param expectedTimeOnShelfHours time until the last unit clears at the
 *                                 chosen list price, queue depth included
 * @param evPerHour                {@code evGross ÷ (T_run_share +
 *                                 timeOnShelfHours × attentionFraction)},
 *                                 capped at {@code evGross} for items that
 *                                 clear in under an hour (see
 *                                 {@link #capRate})
 * @param listRatio                the price ratio (list price ÷ market
 *                                 median) the engine chose because it
 *                                 maximises EV/hour — the actionable
 *                                 "list at X × median" recommendation
 */
public record Valuation(
        double expectedNet,
        double sigmaNet,
        double evGross,
        double expectedTimeOnShelfHours,
        double evPerHour,
        double listRatio) {

    /**
     * Rate honesty for fast sellers: an item that clears in under an
     * hour can never earn more than its total profit within that hour —
     * the flip is a one-shot at the model's horizon, not a repeatable
     * pump. Without this, a small attention fraction turns a 500g flip
     * that sells in minutes into a five-digit "gil per hour" headline.
     * Items on the shelf for an hour or more keep the attention-adjusted
     * rate untouched: there the denominator deliberately discounts
     * parked time.
     */
    public static double capRate(double evPerHour, double evGross, double timeOnShelfHours) {
        if (timeOnShelfHours < 1.0) return Math.min(evPerHour, evGross);
        return evPerHour;
    }

    /**
     * Re-project this valuation's {@link #evPerHour()} at a different
     * attention fraction — used by the retainer partition to compare
     * "sell actively" against "park with retainers overnight" on the same
     * candidate.
     * {@code runShareSeconds} is the {@link UserPrefs#tRunShareSeconds()}
     * that was used to derive the original number.
     */
    public double evPerHourAt(double attentionFraction, double runShareSeconds) {
        double runShareHours = runShareSeconds / 3600.0;
        double denom = runShareHours + expectedTimeOnShelfHours * attentionFraction;
        double rate = denom > 0.0 ? evGross / denom : 0.0;
        return capRate(rate, evGross, expectedTimeOnShelfHours);
    }
}
