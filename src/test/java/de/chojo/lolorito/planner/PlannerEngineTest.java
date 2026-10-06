/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

import de.chojo.lolorito.value.Valuation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerEngineTest {

    private static final WorldNode HOME = new WorldNode(1, "Odin", 10, "Light", "Europe");
    private static final WorldNode SIBLING_DC = new WorldNode(2, "Cerberus", 10, "Light", "Europe");
    private static final WorldNode CROSS_DC = new WorldNode(3, "Ragnarok", 20, "Chaos", "Europe");
    private static final WorldNode ANOTHER = new WorldNode(4, "Alpha", 10, "Light", "Europe");
    private static final WorldNode CROSS_REGION = new WorldNode(5, "Jenova", 40, "Aether", "North-America");

    private static PlannerParams params(long budget, int slots, double attention) {
        return new PlannerParams(
                1, 10, budget, slots, attention, 1.0, 500, 5, 250, 15, 45, 200_000, 0, 40, 20, 0.05, 6.0);
    }

    private static Candidate cand(
            String key, int itemId, WorldNode w, int qty, int buyPrice, double evGross, double shelfHours) {
        return cand(key, itemId, w, qty, buyPrice, evGross, shelfHours, 1);
    }

    /**
     * Same as {@link #cand(String, int, WorldNode, int, int, double, double)}
     * but with an explicit stack size. Tests that pin inventory-limit
     * behaviour use {@code stackSize = 1} (non-stackable gear) so
     * {@code slotsConsumed == qty} — the historical assumption the older
     * tests were written against.
     */
    private static Candidate cand(
            String key,
            int itemId,
            WorldNode w,
            int qty,
            int buyPrice,
            double evGross,
            double shelfHours,
            int stackSize) {
        var valuation = new Valuation(
                buyPrice + evGross / Math.max(1, qty),
                0.0,
                evGross,
                shelfHours,
                evGross / Math.max(0.001, shelfHours),
                1.0);
        return Candidate.simple(
                key,
                itemId,
                "Item#" + itemId,
                false,
                w.worldId(),
                w.worldName(),
                w.dataCenterId(),
                w.dataCenterName(),
                w.regionName(),
                qty,
                buyPrice,
                stackSize,
                PlanAction.RESALE,
                valuation);
    }

    @Test
    void emptyCandidateSetProducesEmptyPlan() {
        var plan = PlannerEngine.plan(HOME, List.of(), params(1_000_000, 100, 8.0));
        assertTrue(plan.stops().isEmpty());
        assertEquals(0, plan.totalBuyCost());
        assertEquals(0, plan.totalQty());
    }

    @Test
    void singleCandidateAtSourceWorldMakesOneStop() {
        var c = cand("k1", 42, SIBLING_DC, 5, 100, 50_000, 1.0);
        var plan = PlannerEngine.plan(HOME, List.of(c), params(1_000_000, 100, 8.0));
        assertEquals(1, plan.stops().size());
        var stop = plan.stops().getFirst();
        assertEquals(SIBLING_DC.worldId(), stop.worldId());
        assertEquals(1, stop.buys().size());
        assertEquals(500, stop.buyCost());
        assertEquals(15, stop.hopSecondsFromPrev(), "same-DC hop = tDcSeconds");
        assertEquals(15, plan.hopSecondsToHome(), "return-home is the same-DC cost");
    }

    @Test
    void samePlusCrossDcHopIsHigherThanTwoSameDc() {
        // Same total EV, but the cross-DC plan pays tRegionSeconds (90s round trip vs. 30s).
        // With hopWeight = 500 gil/s, same-DC "costs" 15_000, cross-DC "costs" 45_000.
        // At EV = 30_000, the same-DC candidate is profitable, the cross-DC one is not.
        var sameDc = cand("A", 1, SIBLING_DC, 1, 100, 30_000, 1.0);
        var crossDc = cand("B", 1, CROSS_DC, 1, 100, 30_000, 1.0);
        var plan = PlannerEngine.plan(HOME, List.of(sameDc, crossDc), params(1_000_000, 100, 8.0));
        assertEquals(1, plan.stops().size(), "single-source-per-item leaves exactly one winner");
        assertEquals(SIBLING_DC.worldId(), plan.stops().getFirst().worldId());
    }

    @Test
    void hopCostCanKillSingleTinyStop() {
        // A tiny EV that doesn't cover the hop cost should not make a plan.
        // Hop cost same-DC round trip = 30 sec × 500 gil/sec = 15_000 gil.
        var c = cand("tiny", 1, SIBLING_DC, 1, 100, 1_000, 1.0);
        var plan = PlannerEngine.plan(HOME, List.of(c), params(1_000_000, 100, 8.0));
        assertTrue(plan.stops().isEmpty(), "hop cost exceeds EV — solver should skip the trip");
    }

    @Test
    void inventoryLimitDropsHigherQtyPicks() {
        // Slots = 3. Non-stackable gear (stackSize=1) so slots == qty and
        // the 5-unit pick doesn't fit; the 3-unit one does.
        var big = cand("big", 1, SIBLING_DC, 5, 100, 20_000, 1.0, 1);
        var small = cand("small", 2, SIBLING_DC, 3, 100, 20_000, 1.0, 1);
        var plan = PlannerEngine.plan(HOME, List.of(big, small), params(1_000_000, 3, 8.0));
        assertEquals(1, plan.stops().size());
        assertEquals(3, plan.totalQty(), "only the 3-slot pick fits into inventory");
    }

    @Test
    void stackableItemsCollapseIntoSingleSlot() {
        // 40 units of a stack-999 item = 1 slot; picks fit even under a
        // tight inventory cap. Regression: previously 40 units ate 40 slots.
        var stackable = cand("stack", 5, SIBLING_DC, 40, 100, 20_000, 1.0, 999);
        var plan = PlannerEngine.plan(HOME, List.of(stackable), params(1_000_000, 1, 8.0));
        assertEquals(1, plan.stops().size(), "40 stackable units occupy 1 slot");
        assertEquals(40, plan.totalQty());
    }

    @Test
    void budgetLimitDropsExpensivePicks() {
        var affordable = cand("cheap", 1, SIBLING_DC, 1, 100, 50_000, 1.0);
        var pricey = cand("bank", 2, SIBLING_DC, 1, 500_000, 50_000, 1.0);
        var plan = PlannerEngine.plan(HOME, List.of(affordable, pricey), params(200_000, 100, 8.0));
        assertEquals(1, plan.stops().size());
        assertEquals(100, plan.totalBuyCost());
    }

    @Test
    void attentionBudgetKillsSlowFlips() {
        // Attention 1 h × attentionFraction 1.0 → 1 h capacity.
        // slow costs 2 h shelf time · 1 unit = 2 → over-cap.
        var slow = cand("slow", 1, SIBLING_DC, 1, 100, 100_000, 2.0);
        var plan = PlannerEngine.plan(HOME, List.of(slow), params(1_000_000, 100, 1.0));
        assertTrue(plan.stops().isEmpty());
    }

    @Test
    void maxWorldsCapEnforced() {
        // Three worlds with equal-value candidates but maxWorlds = 2 — solver must pick 2.
        var candidates = new ArrayList<Candidate>();
        candidates.add(cand("a", 1, SIBLING_DC, 1, 100, 30_000, 0.5));
        candidates.add(cand("b", 2, ANOTHER, 1, 100, 30_000, 0.5));
        candidates.add(cand("c", 3, CROSS_DC, 1, 100, 30_000, 0.5));
        var p = new PlannerParams(1, 10, 1_000_000, 100, 8.0, 1.0, 500, 2, 250, 15, 45, 200_000, 0, 40, 20, 0.05, 6.0);
        var plan = PlannerEngine.plan(HOME, candidates, p);
        assertTrue(plan.stops().size() <= 2, "solver must respect maxWorlds cap");
    }

    @Test
    void stopsAreOrderedBySpendWithinSameHopCost() {
        // Two same-DC stops → both permutations have identical hop cost.
        // Tiebreaker in HopOrderer: biggest spend at stop 0.
        var bigSpend = cand("big", 1, SIBLING_DC, 5, 200, 100_000, 0.5);
        var lilSpend = cand("lil", 2, ANOTHER, 1, 50, 100_000, 0.5);
        var plan = PlannerEngine.plan(HOME, List.of(bigSpend, lilSpend), params(1_000_000, 100, 8.0));
        assertEquals(2, plan.stops().size());
        assertEquals(
                SIBLING_DC.worldId(),
                plan.stops().getFirst().worldId(),
                "biggest-spend stop should come first when hop cost ties");
    }

    @Test
    void negativeEvCandidatesFilteredEarly() {
        var loss = cand("loser", 1, SIBLING_DC, 1, 100, -1_000, 1.0);
        var plan = PlannerEngine.plan(HOME, List.of(loss), params(1_000_000, 100, 8.0));
        assertTrue(plan.stops().isEmpty());
    }

    @Test
    void singleSourceConstraintPerItem() {
        // Two candidates for item 42 from different worlds — only one may survive.
        var a = cand("A", 42, SIBLING_DC, 1, 100, 20_000, 0.5);
        var b = cand("B", 42, ANOTHER, 1, 100, 20_000, 0.5);
        var plan = PlannerEngine.plan(HOME, List.of(a, b), params(1_000_000, 100, 8.0));
        long occurrences = plan.stops().stream()
                .flatMap(s -> s.buys().stream())
                .filter(bx -> bx.itemId() == 42)
                .count();
        assertEquals(1, occurrences, "each item id must appear at most once in the plan");
    }

    @Test
    void plannedObjectiveSubtractsHopCost() {
        // Solver's objective = EV − hop_cost.
        var c = cand("k1", 1, SIBLING_DC, 5, 100, 50_000, 1.0);
        var plan = PlannerEngine.plan(HOME, List.of(c), params(1_000_000, 100, 8.0));
        int expectedHopSeconds = 15 + 15; // out + return
        double expectedObjective = 50_000.0 - expectedHopSeconds * 500.0;
        assertEquals(expectedObjective, plan.objective(), 1e-6);
    }

    @Test
    void insufficientAttentionDoesNotCrashOnInfiniteShelf() {
        // shelfHours = 0 → evPerHour → infinity in the model, but candidate has no valid attention consumption
        // (division would blow up). The engine must simply skip candidates whose attention is infinite.
        var v = new Valuation(200, 0, 10_000, Double.POSITIVE_INFINITY, 0, 1.0);
        var c = Candidate.simple(
                "inf",
                1,
                "x",
                false,
                SIBLING_DC.worldId(),
                SIBLING_DC.worldName(),
                SIBLING_DC.dataCenterId(),
                SIBLING_DC.dataCenterName(),
                SIBLING_DC.regionName(),
                1,
                100,
                1,
                PlanAction.RESALE,
                v);
        var plan = PlannerEngine.plan(HOME, List.of(c), params(1_000_000, 100, 8.0));
        assertFalse(plan.stops().stream().flatMap(s -> s.buys().stream()).anyMatch(b -> "inf".equals(b.uniqueKey())));
    }

    @Test
    void crossRegionCandidateProducesEmptyPlan() {
        // A juicy North-American listing when the home world is on Europe:
        // reachable? No. HopCoster.hopSeconds returns Integer.MAX_VALUE,
        // objective goes hugely negative, no subset survives.
        var c = cand("nope", 42, CROSS_REGION, 5, 100, 50_000, 1.0);
        var plan = PlannerEngine.plan(HOME, List.of(c), params(1_000_000, 100, 8.0));
        assertTrue(plan.stops().isEmpty(), "cross-region candidate cannot be planned");
    }

    // -- Retainer-overnight ------------------------------------------------

    private static PlannerParams withRetainer(long budget, int slots, double attention, int retainerSlots) {
        return new PlannerParams(
                1, 10, budget, slots, attention, 1.0, 500, 5, 250, 15, 45, 200_000, retainerSlots, 40, 20, 0.05, 6.0);
    }

    @Test
    void slowMoverGoesToRetainerBasketWhenSlotsAvailable() {
        // shelfHours = 20 → well above the 6h threshold; retainer attention (0.05)
        // vs active attention (1.0) massively improves retainer EV/hr.
        var slow = cand("slow", 1, SIBLING_DC, 5, 100, 50_000, 20.0);
        var plan = PlannerEngine.plan(HOME, List.of(slow), withRetainer(1_000_000, 100, 8.0, 20));
        assertEquals(1, plan.retainerBasket().size(), "slow mover should be a retainer pick");
        assertTrue(plan.stops().isEmpty(), "no hop stops when only the retainer basket picks up");
        assertEquals(500L, plan.retainerSpend(), "retainer spend counts 5 × 100");
        assertEquals(5, plan.retainerQty());
    }

    @Test
    void retainerBasketReportsItsOwnAttentionAndObjective() {
        var slow = cand("slow", 1, SIBLING_DC, 5, 100, 50_000, 20.0);
        var plan = PlannerEngine.plan(HOME, List.of(slow), withRetainer(1_000_000, 100, 8.0, 20));
        double expectedHours = 30.0 / 3600.0 + 20.0 * 0.05;
        assertEquals(expectedHours, plan.retainerAttentionHours(), 1e-9);
        assertEquals(0.0, plan.totalAttentionHours(), 1e-9, "live attention excludes the retainer basket");
        assertEquals(plan.objective(), plan.retainerObjective(), 1e-9, "with no live stops the whole objective is retainer");
        assertTrue(plan.retainerObjective() <= plan.retainerEvGross());
    }

    @Test
    void fastMoverStaysInHopBasket() {
        // Short shelf → active attention still wins → hop stop, not retainer.
        var fast = cand("fast", 1, SIBLING_DC, 5, 100, 50_000, 0.5);
        var plan = PlannerEngine.plan(HOME, List.of(fast), withRetainer(1_000_000, 100, 8.0, 20));
        assertTrue(plan.retainerBasket().isEmpty(), "fast mover doesn't qualify");
        assertEquals(1, plan.stops().size());
    }

    @Test
    void retainerSlotsZeroDisablesTheBasket() {
        var slow = cand("slow", 1, SIBLING_DC, 5, 100, 50_000, 20.0);
        var plan = PlannerEngine.plan(HOME, List.of(slow), withRetainer(1_000_000, 100, 8.0, 0));
        assertTrue(plan.retainerBasket().isEmpty(), "retainerSlots=0 → no retainer picks");
    }

    @Test
    void retainerBudgetIsCappedAtItsShareOfTheTotal() {
        // Retainer picks are capped at 40 % of the total budget (2_000g of
        // the 5_000g here). The 500g slow mover fits inside that share; the
        // fast mover keeps the remaining 3_000g for the hop plan.
        var slow = cand("slow", 1, SIBLING_DC, 5, 100, 50_000, 20.0);
        var fast = cand("fast", 2, ANOTHER, 3, 100, 30_000, 0.5);
        var plan = PlannerEngine.plan(HOME, List.of(slow, fast), withRetainer(5_000, 100, 8.0, 20));
        assertEquals(1, plan.retainerBasket().size(), "slow mover fits in the retainer share");
        assertEquals(1, plan.stops().size(), "hop plan still has budget left for the fast mover");
    }

    @Test
    void retainerBudgetShareCanExcludeAnOversizedPick() {
        // 40 % of 1_000g = 400g, so the 500g slow mover no longer fits the
        // retainer share. It falls back into the hop pool; the hop plan
        // then decides based on its own economics (hop cost vs. EV/hr).
        // The point being tested here is that the retainer share is
        // capped and doesn't just consume everything.
        var slow = cand("slow", 1, SIBLING_DC, 5, 100, 50_000, 20.0);
        var plan = PlannerEngine.plan(HOME, List.of(slow), withRetainer(1_000, 100, 8.0, 20));
        assertTrue(plan.retainerBasket().isEmpty(), "500g pick can't fit into the 400g retainer share");
    }

    @Test
    void retainerBasketRespectsSlotCount() {
        // Ten slow movers, retainerSlots capped at 3.
        var candidates = new ArrayList<Candidate>();
        for (int i = 0; i < 10; i++) {
            candidates.add(cand("slow-" + i, 100 + i, SIBLING_DC, 1, 100, 10_000, 20.0));
        }
        var plan = PlannerEngine.plan(HOME, candidates, withRetainer(1_000_000, 100, 8.0, 3));
        assertEquals(3, plan.retainerBasket().size());
    }
}
