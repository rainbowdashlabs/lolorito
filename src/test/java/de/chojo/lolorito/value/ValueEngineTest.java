/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueEngineTest {

    private static MarketModel modelAt(int worldId, double priceMedian, double lambda, boolean sufficient) {
        double mu = Math.log(priceMedian);
        var price = new PriceDistribution(mu, 0.0, 100);
        var rate = new SaleRate(lambda * 1.2, lambda, lambda * 0.6);
        return new MarketModel(1, worldId, false, price, rate, 0.0, 0.0, 100, sufficient, false, Instant.now());
    }

    @Test
    void insufficientModelYieldsEmpty() {
        var m = modelAt(1, 1000, 1.0, false);
        assertFalse(ValueEngine.value(m, 500, 1, UserPrefs.defaults()).isPresent());
    }

    @Test
    void positiveMarginProducesPositiveEv() {
        var m = modelAt(1, 1000, 1.0, true);
        var v = ValueEngine.value(m, 500, 1, UserPrefs.defaults()).orElseThrow();
        assertTrue(v.evGross() > 0, "Buying at 500, selling around 1000 — expected positive EV");
        assertTrue(v.evPerHour() > 0);
        // The engine picks the EV/hour-argmax anchor; whatever it chose,
        // proceeds must be that anchor's price net of tax — price and speed
        // read at the SAME point.
        assertEquals(v.listRatio() * 1000 * 0.95, v.expectedNet(), 1e-6);
        assertTrue(
                v.listRatio() == 0.95 || v.listRatio() == 1.00 || v.listRatio() == 1.05,
                "listRatio must be one of the fitted anchors, got " + v.listRatio());
    }

    @Test
    void chosenAnchorMaximisesEvPerHour() {
        // λ(0.95) = 1.2, λ(1.00) = 1.0, λ(1.05) = 0.6 — the aggressive
        // anchor clears fastest. For a short-attention flip the argmax
        // should be the aggressive one, not the highest sticker price.
        var m = modelAt(1, 1000, 1.0, true);
        var v = ValueEngine.value(m, 500, 1, UserPrefs.defaults()).orElseThrow();
        assertEquals(0.95, v.listRatio(), 1e-9);
    }

    @Test
    void queueDepthExtendsShelfTime() {
        var m = modelAt(1, 1000, 2.0, true);
        var empty = ValueEngine.value(m, 500, 1, 0, UserPrefs.defaults()).orElseThrow();
        var queued = ValueEngine.value(m, 500, 1, 20, UserPrefs.defaults()).orElseThrow();
        assertTrue(
                queued.expectedTimeOnShelfHours() > empty.expectedTimeOnShelfHours() * 10,
                "20 units ahead in the queue must dominate the wait for a single unit");
        assertTrue(queued.evPerHour() < empty.evPerHour());
    }

    @Test
    void undercutPressureLowersRealisedPrice() {
        var calm = modelAt(1, 1000, 1.0, true);
        var contested = new MarketModel(
                1,
                1,
                false,
                calm.price(),
                calm.saleRate(),
                30.0, // 30 undercuts/hour — a bot camping the key
                0.0,
                100,
                true,
                false,
                calm.fittedAt());
        var calmV = ValueEngine.value(calm, 500, 1, UserPrefs.defaults()).orElseThrow();
        var contestedV =
                ValueEngine.value(contested, 500, 1, UserPrefs.defaults()).orElseThrow();
        assertTrue(
                contestedV.expectedNet() <= calmV.expectedNet(),
                "Undercut pressure must never raise realised proceeds");
        assertTrue(
                contestedV.expectedTimeOnShelfHours() > calmV.expectedTimeOnShelfHours() * 5,
                "A bot camping the key parks the listing off-floor — shelf time must stretch");
        assertTrue(
                contestedV.evPerHour() < calmV.evPerHour() * 0.5,
                "The whole point: a bot-camped flip must rank far below a calm one");
    }

    @Test
    void higherRateCollapsesShelfTimeAndCapsSubHourRate() {
        var slow = modelAt(1, 1000, 0.5, true);
        var fast = modelAt(1, 1000, 5.0, true);
        var slowV = ValueEngine.value(slow, 500, 1, UserPrefs.defaults()).orElseThrow();
        var fastV = ValueEngine.value(fast, 500, 1, UserPrefs.defaults()).orElseThrow();
        assertTrue(fastV.expectedTimeOnShelfHours() < slowV.expectedTimeOnShelfHours());
        // Sub-hour flips report the profit actually attainable within the
        // hour — never an extrapolated repeat rate.
        assertTrue(fastV.expectedTimeOnShelfHours() < 1.0);
        assertEquals(fastV.evGross(), fastV.evPerHour(), 1e-9);
        // The slow item stays attention-adjusted (parked time discounted).
        assertTrue(slowV.expectedTimeOnShelfHours() >= 1.0);
        assertTrue(slowV.evPerHour() > slowV.evGross());
    }

    @Test
    void largerQtyExtendsShelfTimeLinearly() {
        var m = modelAt(1, 1000, 2.0, true);
        var one = ValueEngine.value(m, 500, 1, UserPrefs.defaults()).orElseThrow();
        var ten = ValueEngine.value(m, 500, 10, UserPrefs.defaults()).orElseThrow();
        assertEquals(one.expectedTimeOnShelfHours() * 10, ten.expectedTimeOnShelfHours(), 1e-9);
        assertEquals(one.evGross() * 10, ten.evGross(), 1e-6);
    }

    @Test
    void attentionFractionScalesEvPerHour() {
        // Two prefs, same everything except attentionFraction. Lower attention
        // means more of the time doesn't "cost" you → higher gil/hour.
        var m = modelAt(1, 1000, 0.1, true);
        var attentive = new UserPrefs(0.05, 1.0, 30.0);
        var retainer = new UserPrefs(0.05, 0.05, 30.0);
        var a = ValueEngine.value(m, 500, 10, attentive).orElseThrow();
        var r = ValueEngine.value(m, 500, 10, retainer).orElseThrow();
        assertTrue(
                r.evPerHour() > a.evPerHour() * 3,
                "Retainer-tolerant player should see materially higher gil/hour for a slow flip");
    }

    @Test
    void unfavourableTradeShowsNegativeEv() {
        var m = modelAt(1, 1000, 1.0, true);
        // Buying at 2000, expected net ~950 → negative.
        var v = ValueEngine.value(m, 2000, 1, UserPrefs.defaults()).orElseThrow();
        assertTrue(v.evGross() < 0);
        assertTrue(v.evPerHour() < 0);
    }
}
