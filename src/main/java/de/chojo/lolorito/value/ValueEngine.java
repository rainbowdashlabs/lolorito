/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import java.util.List;
import java.util.Optional;

/**
 * Pure scoring — no I/O. Composes a {@link Valuation} for the three action
 * kinds the item DAG supports today: <b>resale</b>, <b>desynth</b>, and
 * <b>craft</b>.
 *
 * <p>All three share the same objective — attention-adjusted gil per
 * hour — and the same {@link Valuation} record shape, so downstream code
 * can rank them against each other on one axis.
 *
 * <p>Resale and craft choose the listing price themselves: the engine
 * evaluates every {@link SaleRate} anchor ratio and keeps the one that
 * maximises EV/hour, so proceeds and sale speed are always read at the
 * <em>same</em> price point (the old code priced at the distribution mean
 * but read λ at the median — optimistic on both axes at once).
 */
public final class ValueEngine {

    /** The listing ratios the engine considers — the fitted λ anchors. */
    private static final double[] LIST_RATIOS = {0.95, 1.00, 1.05};

    /**
     * When undercutters race us, the realised price after losing the race
     * approximates "one step below the floor" — the aggressive anchor.
     */
    private static final double UNDERCUT_FLOOR_RATIO = 0.95;

    private ValueEngine() {}

    // -- Resale -------------------------------------------------------------

    /**
     * Backwards-compatible overload: no knowledge of the home-world queue.
     */
    public static Optional<Valuation> value(MarketModel model, double buyPrice, int qty, UserPrefs prefs) {
        return value(model, buyPrice, qty, 0, prefs);
    }

    /**
     * Score buying {@code qty} units at {@code buyPrice} and reselling them
     * on the home world governed by {@code model}. Empty when the model is
     * insufficient.
     *
     * @param depthAhead units currently listed on the home world at or
     *                   below the market median — the queue your units
     *                   join. FFXIV sells cheapest-first, so those units
     *                   absorb demand before yours do.
     */
    public static Optional<Valuation> value(
            MarketModel model, double buyPrice, int qty, int depthAhead, UserPrefs prefs) {
        if (!model.sufficient() || qty <= 0) return Optional.empty();
        return bestListing(model, buyPrice, qty, depthAhead, prefs);
    }

    /**
     * Evaluate every anchor ratio and keep the EV/hour argmax. Proceeds,
     * sale rate, and shelf time are all read at the same listing price.
     */
    private static Optional<Valuation> bestListing(
            MarketModel model, double unitCost, int qty, int depthAhead, UserPrefs prefs) {
        return bestListing(model, unitCost, qty, depthAhead, prefs, 0.0);
    }

    /**
     * @param extraActiveHours additional full-attention wall-clock the
     *                         action costs before anything is listed —
     *                         craft time, mostly. Charged like run share.
     */
    private static Optional<Valuation> bestListing(
            MarketModel model, double unitCost, int qty, int depthAhead, UserPrefs prefs, double extraActiveHours) {
        double median = model.price().medianPrice();
        if (median <= 0) return Optional.empty();

        // Full log-normal spread in gil, after tax — describes the market's
        // dispersion independent of which anchor we list at.
        double sigma = model.price().sigma();
        double sigmaNet = Math.exp(model.price().mu() + sigma * sigma / 2.0)
                * Math.sqrt(Math.expm1(sigma * sigma))
                * (1.0 - prefs.mbTax());

        double runShareHours = prefs.tRunShareSeconds() / 3600.0 + Math.max(0.0, extraActiveHours);
        Valuation best = null;
        // The anchor argmax runs on the UNCAPPED rate — capping inside the
        // comparison would make the engine prefer slower anchors purely
        // because their metric escapes the sub-hour cap. The chosen
        // valuation then reports the capped rate (see Valuation#capRate).
        double bestRawRate = Double.NEGATIVE_INFINITY;
        for (double ratio : LIST_RATIOS) {
            double lambda = model.saleRate().at(ratio);
            if (lambda <= 0.0) continue;

            // Undercut race: with undercuts arriving at λ_uc and sales at λ,
            // we sell at our price with probability λ/(λ+λ_uc); losing the
            // race realises roughly one step below the floor AND parks the
            // listing off-floor, so the effective sale rate drops by the
            // same factor — bot-camped keys get expensive on both axes.
            // λ_uc = 0 (unmeasured or calm market) degrades to the plain
            // "sell at list price" case.
            double lambdaUc = Math.max(0.0, model.lambdaUndercut());
            double pSellFirst = lambda / (lambda + lambdaUc);
            double realisedRatio = ratio * pSellFirst + UNDERCUT_FLOOR_RATIO * (1.0 - pSellFirst);
            double lambdaEffective = lambda * pSellFirst;
            if (lambdaEffective <= 0.0) continue;

            double expectedNet = realisedRatio * median * (1.0 - prefs.mbTax());
            double evGross = (expectedNet - unitCost) * qty;
            // Queue depth measured at the median — an approximation for the
            // off-median anchors, conservative for the aggressive one.
            double timeOnShelfHours = (depthAhead + qty) / lambdaEffective;

            double denom = runShareHours + timeOnShelfHours * prefs.attentionFraction();
            double rawRate = denom > 0.0 ? evGross / denom : 0.0;

            if (best == null || rawRate > bestRawRate) {
                bestRawRate = rawRate;
                best = new Valuation(
                        expectedNet,
                        sigmaNet,
                        evGross,
                        timeOnShelfHours,
                        Valuation.capRate(rawRate, evGross, timeOnShelfHours),
                        ratio);
            }
        }
        return Optional.ofNullable(best);
    }

    // -- Desynth ------------------------------------------------------------

    /**
     * Score buying {@code qty} units of a desynthable item at {@code
     * buyPrice}, tearing them apart, and reselling each component on the
     * home world.
     *
     * <p>Components are priced at their market median (anchor 1.0) — the
     * honest zero-information listing point. They go up on retainers
     * <em>simultaneously</em>, so wall-clock to clear is the <em>slowest</em>
     * component's clear time, not the sum (the old sum overstated the
     * attention cost several-fold for multi-component sources and made
     * desynth lose ranking fights it should win).
     *
     * <p>Empty when no component has a sufficient model — nothing to price
     * against. Skill success is treated as {@code 1.0} for now; per-class
     * skill levels land later.
     */
    public static Optional<Valuation> valueDesynth(
            double buyPrice, int qty, List<ComponentPricing> components, UserPrefs prefs) {
        if (qty <= 0 || components.isEmpty()) return Optional.empty();

        double expectedNetPerSource = 0.0;
        double sigmaSqPerSource = 0.0;
        double slowestClearHoursPerSource = 0.0;
        boolean anySufficient = false;

        for (ComponentPricing c : components) {
            var model = c.sellModel();
            if (model == null || !model.sufficient()) continue;
            anySufficient = true;

            double compMedian = model.price().medianPrice();
            double compNet = compMedian * (1.0 - prefs.mbTax());
            double compSigmaLog = model.price().sigma();
            double compSigma = compMedian * Math.sqrt(Math.expm1(compSigmaLog * compSigmaLog)) * (1.0 - prefs.mbTax());

            expectedNetPerSource += c.avgQtyPerSource() * compNet;
            sigmaSqPerSource += Math.pow(c.avgQtyPerSource() * compSigma, 2);

            // Clear time of THIS component's share, selling at median.
            double lambda = model.saleRate().at(1.0);
            if (lambda > 0.0) {
                slowestClearHoursPerSource = Math.max(slowestClearHoursPerSource, c.avgQtyPerSource() / lambda);
            } else {
                slowestClearHoursPerSource = Double.POSITIVE_INFINITY;
                break;
            }
        }

        if (!anySufficient) return Optional.empty();

        double expectedNet = expectedNetPerSource;
        double sigmaNet = Math.sqrt(sigmaSqPerSource);
        double evGross = (expectedNet - buyPrice) * qty;
        double timeOnShelfHours = slowestClearHoursPerSource * qty;

        double runShareHours = prefs.tRunShareSeconds() / 3600.0;
        double denom = runShareHours + timeOnShelfHours * prefs.attentionFraction();
        double evPerHour = Valuation.capRate(denom > 0.0 ? evGross / denom : 0.0, evGross, timeOnShelfHours);

        return Optional.of(new Valuation(expectedNet, sigmaNet, evGross, timeOnShelfHours, evPerHour, 1.0));
    }

    // -- Craft --------------------------------------------------------------

    /**
     * Score crafting {@code qty} units of a product, sourcing every
     * ingredient at its {@code cheapestBuy} price and selling the product
     * on the home world governed by {@code productModel}.
     *
     * <p>Empty when the product model isn't sufficient or any ingredient
     * lacks a price — we can't confidently value a craft with a hole.
     */
    public static Optional<Valuation> valueCraft(
            MarketModel productModel, int qty, int recipeYield, List<ComponentPricing> ingredients, UserPrefs prefs) {
        if (!productModel.sufficient() || qty <= 0 || recipeYield <= 0) return Optional.empty();

        double perProductCost = 0.0;
        for (ComponentPricing ing : ingredients) {
            if (ing.cheapestBuy() == null) return Optional.empty();
            perProductCost += (ing.avgQtyPerSource() * ing.cheapestBuy()) / recipeYield;
        }
        return valueCraftAtCost(productModel, qty, perProductCost, prefs);
    }

    /**
     * Same output as {@link #valueCraft} but takes an already-computed
     * per-product cost. Used by the multi-level chain planner
     * that decides each ingredient's cheapest source itself and only
     * hands the resulting cost to the valuation.
     */
    public static Optional<Valuation> valueCraftAtCost(
            MarketModel productModel, int qty, double perProductCost, UserPrefs prefs) {
        return valueCraftAtCost(productModel, qty, perProductCost, prefs, 0.0);
    }

    /**
     * Same, but charges the craft's own wall-clock: {@code qty ×
     * craftSecondsPerUnit} of full-attention time in the denominator.
     * Crafting a 99-batch is not free per attention-hour.
     */
    public static Optional<Valuation> valueCraftAtCost(
            MarketModel productModel, int qty, double perProductCost, UserPrefs prefs, double craftSecondsPerUnit) {
        if (!productModel.sufficient() || qty <= 0) return Optional.empty();
        double craftHours = Math.max(0.0, craftSecondsPerUnit) * qty / 3600.0;
        return bestListing(productModel, perProductCost, qty, 0, prefs, craftHours);
    }

    /**
     * HQ-aware craft valuation. Crafting toward an HQ product is a coin
     * flip weighted by gear/skill — {@code hqChance} of the batch comes out
     * HQ and sells against {@code hqModel}, the rest sells NQ. The old
     * behaviour (pair an HQ price with a 100% HQ outcome) overstated HQ
     * crafts badly.
     *
     * <p>Degrades gracefully: with no usable HQ model the NQ valuation is
     * returned as-is; with no usable NQ model the HQ branch is scaled by
     * {@code hqChance} and the NQ share contributes nothing (conservative —
     * NQ leftovers still usually sell for something).
     */
    public static Optional<Valuation> valueCraftMixed(
            MarketModel nqModel,
            MarketModel hqModel,
            double hqChance,
            int qty,
            double perProductCost,
            UserPrefs prefs) {
        return valueCraftMixed(nqModel, hqModel, hqChance, qty, perProductCost, prefs, 0.0);
    }

    /** HQ mixture with the craft's own wall-clock charged — see {@link #valueCraftAtCost}. */
    public static Optional<Valuation> valueCraftMixed(
            MarketModel nqModel,
            MarketModel hqModel,
            double hqChance,
            int qty,
            double perProductCost,
            UserPrefs prefs,
            double craftSecondsPerUnit) {
        double p = Math.min(1.0, Math.max(0.0, hqChance));
        Optional<Valuation> nq = nqModel == null
                ? Optional.empty()
                : valueCraftAtCost(nqModel, qty, perProductCost, prefs, craftSecondsPerUnit);
        Optional<Valuation> hq = hqModel == null || p <= 0.0
                ? Optional.empty()
                : valueCraftAtCost(hqModel, qty, perProductCost, prefs, craftSecondsPerUnit);

        double craftHours = Math.max(0.0, craftSecondsPerUnit) * qty / 3600.0;
        if (hq.isEmpty()) return nq;
        if (nq.isEmpty()) return hq.map(v -> scale(v, p, prefs, craftHours, perProductCost, qty));

        Valuation a = hq.get();
        Valuation b = nq.get();
        double expectedNet = p * a.expectedNet() + (1 - p) * b.expectedNet();
        // Mixture variance: weighted branch variances + spread between the
        // branch means.
        double sigmaNet = Math.sqrt(p * a.sigmaNet() * a.sigmaNet()
                + (1 - p) * b.sigmaNet() * b.sigmaNet()
                + p * (1 - p) * Math.pow(a.expectedNet() - b.expectedNet(), 2));
        double evGross = p * a.evGross() + (1 - p) * b.evGross();
        double time = p * a.expectedTimeOnShelfHours() + (1 - p) * b.expectedTimeOnShelfHours();
        double runShareHours = prefs.tRunShareSeconds() / 3600.0 + craftHours;
        double denom = runShareHours + time * prefs.attentionFraction();
        double evPerHour = Valuation.capRate(denom > 0.0 ? evGross / denom : 0.0, evGross, time);
        double listRatio = p >= 0.5 ? a.listRatio() : b.listRatio();
        return Optional.of(new Valuation(expectedNet, sigmaNet, evGross, time, evPerHour, listRatio));
    }

    /**
     * HQ-only branch weighted by its probability; the NQ share earns
     * nothing. The cost side must NOT scale with the probability — the
     * crafter pays materials for the whole batch whatever quality comes
     * out — so profit is recomputed from the scaled proceeds instead of
     * scaling the already-cost-subtracted {@code evGross} (which silently
     * discounted {@code (1 − p)} of the material bill and overstated
     * marginal crafts, sometimes past the profitability gate).
     */
    private static Valuation scale(
            Valuation v, double p, UserPrefs prefs, double craftHours, double perProductCost, int qty) {
        double expectedNet = v.expectedNet() * p;
        double evGross = (expectedNet - perProductCost) * qty;
        double runShareHours = prefs.tRunShareSeconds() / 3600.0 + craftHours;
        double denom = runShareHours + v.expectedTimeOnShelfHours() * prefs.attentionFraction();
        double evPerHour =
                Valuation.capRate(denom > 0.0 ? evGross / denom : 0.0, evGross, v.expectedTimeOnShelfHours());
        return new Valuation(
                expectedNet, v.sigmaNet(), evGross, v.expectedTimeOnShelfHours(), evPerHour, v.listRatio());
    }
}
