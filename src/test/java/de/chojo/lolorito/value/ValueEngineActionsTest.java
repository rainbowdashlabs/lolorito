/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueEngineActionsTest {

    private static MarketModel model(int itemId, double median, double lambda, boolean sufficient) {
        double mu = Math.log(median);
        var price = new PriceDistribution(mu, 0.0, 100);
        var rate = new SaleRate(lambda * 1.2, lambda, lambda * 0.6);
        return new MarketModel(itemId, 1, false, price, rate, 0.0, 0.0, 100, sufficient, false, Instant.now());
    }

    // -- Rate cap -----------------------------------------------------------

    @Test
    void fastSellerRateIsCappedAtTotalProfit() {
        // λ is high → the flip clears in minutes. The uncapped
        // attention-adjusted rate would extrapolate a ~500g profit into a
        // five-digit gil/hour headline; the cap pins the rate to the profit
        // actually attainable within that hour.
        var v = ValueEngine.value(model(1, 1000, 10.0, true), 500, 1, UserPrefs.defaults())
                .orElseThrow();
        assertTrue(v.expectedTimeOnShelfHours() < 1.0);
        assertEquals(v.evGross(), v.evPerHour(), 1e-9);
    }

    @Test
    void slowSellerKeepsAttentionAdjustedRate() {
        // Shelf time over an hour → parked time is discounted by the
        // attention fraction, so the rate may legitimately exceed a single
        // flip's profit; the cap must not touch this case.
        var v = ValueEngine.value(model(1, 1000, 0.55, true), 500, 1, UserPrefs.defaults())
                .orElseThrow();
        assertTrue(v.expectedTimeOnShelfHours() >= 1.0);
        assertTrue(v.evPerHour() > v.evGross());
    }

    @Test
    void fastDesynthRateIsCappedAtTotalProfit() {
        // Component clears well inside the hour → same cap as resale.
        var comps = List.of(new ComponentPricing(101, 0.5, model(101, 2000, 5.0, true), null));
        var v = ValueEngine.valueDesynth(500, 1, comps, UserPrefs.defaults()).orElseThrow();
        assertTrue(v.expectedTimeOnShelfHours() < 1.0);
        assertEquals(v.evGross(), v.evPerHour(), 1e-9);
    }

    // -- Desynth ------------------------------------------------------------

    @Test
    void desynthWithNoComponentsIsEmpty() {
        var v = ValueEngine.valueDesynth(500, 1, List.of(), UserPrefs.defaults());
        assertFalse(v.isPresent());
    }

    @Test
    void desynthBeatsBuyPriceWhenComponentsAreValuable() {
        // Buy at 500. Each unit yields 0.6 of a 500g component + 0.3 of a 800g component.
        // Expected net per source ~ (0.6·500 + 0.3·800) · 0.95 = (300+240) · 0.95 = 513 → tiny margin.
        var comps = List.of(
                new ComponentPricing(101, 0.6, model(101, 500, 1.0, true), null),
                new ComponentPricing(102, 0.3, model(102, 800, 1.0, true), null));
        var v = ValueEngine.valueDesynth(500, 1, comps, UserPrefs.defaults()).orElseThrow();
        assertTrue(v.evGross() > 0);
        assertTrue(v.expectedNet() > 500);
    }

    @Test
    void desynthWithAllInsufficientComponentsIsEmpty() {
        var comps = List.of(
                new ComponentPricing(101, 0.6, model(101, 500, 1.0, false), null),
                new ComponentPricing(102, 0.3, model(102, 800, 1.0, false), null));
        assertFalse(
                ValueEngine.valueDesynth(500, 1, comps, UserPrefs.defaults()).isPresent());
    }

    // -- Craft --------------------------------------------------------------

    @Test
    void craftPositiveWhenProductBeatsIngredientCost() {
        // Product sells net for 950g after 5 % tax on 1000g median.
        // Ingredients cost 3·100 + 1·200 = 500g → 450g margin.
        var product = model(200, 1000, 2.0, true);
        var ings = List.of(new ComponentPricing(101, 3, null, 100), new ComponentPricing(102, 1, null, 200));
        var v = ValueEngine.valueCraft(product, 1, 1, ings, UserPrefs.defaults())
                .orElseThrow();
        assertTrue(v.evGross() > 0);
        assertTrue(v.evPerHour() > 0);
    }

    @Test
    void craftEmptyWhenIngredientLacksPrice() {
        var product = model(200, 1000, 2.0, true);
        var ings = List.of(
                new ComponentPricing(101, 3, null, 100), new ComponentPricing(102, 1, null, null) // no price
                );
        assertFalse(ValueEngine.valueCraft(product, 1, 1, ings, UserPrefs.defaults())
                .isPresent());
    }

    @Test
    void craftYieldReducesPerProductIngredientCost() {
        // Same ingredients, yield 3 → per-product cost is 500/3, margin bigger.
        var product = model(200, 1000, 2.0, true);
        var ings = List.of(new ComponentPricing(101, 3, null, 100), new ComponentPricing(102, 1, null, 200));
        var yield1 = ValueEngine.valueCraft(product, 1, 1, ings, UserPrefs.defaults())
                .orElseThrow();
        var yield3 = ValueEngine.valueCraft(product, 1, 3, ings, UserPrefs.defaults())
                .orElseThrow();
        assertTrue(yield3.evGross() > yield1.evGross());
    }

    @Test
    void desynthComponentsClearInParallel() {
        // Two components with identical value and rate. Clear time must be
        // governed by the slowest one, NOT their sum — retainers list all
        // components simultaneously.
        var one = List.of(new ComponentPricing(101, 1.0, model(101, 500, 0.5, true), null));
        var two = List.of(
                new ComponentPricing(101, 1.0, model(101, 500, 0.5, true), null),
                new ComponentPricing(102, 1.0, model(102, 500, 0.5, true), null));
        var vOne = ValueEngine.valueDesynth(100, 1, one, UserPrefs.defaults()).orElseThrow();
        var vTwo = ValueEngine.valueDesynth(100, 1, two, UserPrefs.defaults()).orElseThrow();
        assertEquals(
                vOne.expectedTimeOnShelfHours(),
                vTwo.expectedTimeOnShelfHours(),
                1e-9,
                "Adding a parallel component of equal speed must not extend the clear time");
        assertTrue(vTwo.expectedNet() > vOne.expectedNet());
    }

    @Test
    void craftMixtureSitsBetweenPureBranches() {
        var nq = model(200, 1000, 2.0, true);
        var hq = model(200, 3000, 1.0, true);
        double cost = 500;
        var pureNq =
                ValueEngine.valueCraftAtCost(nq, 1, cost, UserPrefs.defaults()).orElseThrow();
        var pureHq =
                ValueEngine.valueCraftAtCost(hq, 1, cost, UserPrefs.defaults()).orElseThrow();
        var mixed = ValueEngine.valueCraftMixed(nq, hq, 0.5, 1, cost, UserPrefs.defaults())
                .orElseThrow();
        assertTrue(mixed.evGross() > pureNq.evGross(), "Half the batch selling HQ must beat pure NQ");
        assertTrue(mixed.evGross() < pureHq.evGross(), "A 50% HQ chance must not be valued as guaranteed HQ");
    }

    @Test
    void craftMixtureDegradesToNqWhenHqModelMissing() {
        var nq = model(200, 1000, 2.0, true);
        var pureNq =
                ValueEngine.valueCraftAtCost(nq, 1, 500, UserPrefs.defaults()).orElseThrow();
        var mixed = ValueEngine.valueCraftMixed(nq, null, 0.5, 1, 500, UserPrefs.defaults())
                .orElseThrow();
        assertEquals(pureNq.evGross(), mixed.evGross(), 1e-9);
    }

    @Test
    void craftHqOnlyBranchChargesTheFullMaterialBill() {
        // No sufficient NQ model → HQ-only fallback. The NQ share of the
        // batch earns nothing but its materials were still paid for, so
        // the pair (expectedNet, evGross) must stay self-consistent:
        // evGross = (expectedNet − cost) × qty. The old scaling multiplied
        // evGross by p and silently discounted (1 − p) of the bill.
        var hq = model(200, 3000, 1.0, true);
        double cost = 1800;
        int qty = 3;
        double p = 0.5;
        var mixed = ValueEngine.valueCraftMixed(null, hq, p, qty, cost, UserPrefs.defaults())
                .orElseThrow();
        assertEquals((mixed.expectedNet() - cost) * qty, mixed.evGross(), 1e-9);

        // And the proceeds side is the HQ branch weighted by its chance.
        var pureHq = ValueEngine.valueCraftAtCost(hq, qty, cost, UserPrefs.defaults())
                .orElseThrow();
        assertEquals(pureHq.expectedNet() * p, mixed.expectedNet(), 1e-9);
    }
}
