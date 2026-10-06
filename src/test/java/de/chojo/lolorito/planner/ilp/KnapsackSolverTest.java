/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner.ilp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnapsackSolverTest {

    private final Solver solver = new KnapsackSolver();

    @Test
    void emptyItemsProducesEmptyResult() {
        var res = solver.solve(List.of(), 1000, 10, 10.0, 10_000);
        assertEquals(0.0, res.value(), 1e-9);
        assertTrue(res.picks().isEmpty());
    }

    @Test
    void singleFittingItemIsPicked() {
        var it = new KnapsackItem("a", 1, 100.0, 50, 1, 0.1);
        var res = solver.solve(List.of(it), 1000, 10, 10.0, 10_000);
        assertEquals(100.0, res.value(), 1e-9);
        assertEquals(1, res.picks().size());
        assertEquals("a", res.picks().getFirst().id());
    }

    @Test
    void groupCollapsesKeepsBestByValue() {
        // Two candidates for the same item id — the higher-value one wins.
        var loBid = new KnapsackItem("lo", 42, 50.0, 100, 1, 0.1);
        var hiBid = new KnapsackItem("hi", 42, 100.0, 100, 1, 0.1);
        var res = solver.solve(List.of(loBid, hiBid), 1000, 10, 10.0, 10_000);
        assertEquals(100.0, res.value(), 1e-9);
        assertEquals(1, res.picks().size());
        assertEquals("hi", res.picks().getFirst().id());
    }

    @Test
    void budgetConstraintDropsOverpricedItems() {
        var affordable = new KnapsackItem("cheap", 1, 100.0, 50, 1, 0.1);
        var tooExpensive = new KnapsackItem("bank", 2, 999.0, 100_000, 1, 0.1);
        var res = solver.solve(List.of(affordable, tooExpensive), 200, 10, 10.0, 10_000);
        assertEquals(100.0, res.value(), 1e-9);
        assertEquals(1, res.picks().size());
        assertEquals("cheap", res.picks().getFirst().id());
    }

    @Test
    void inventoryConstraintForcesTradeoff() {
        // Only 1 slot. Pick the highest-value single item.
        var lo = new KnapsackItem("lo", 1, 50.0, 10, 1, 0.1);
        var hi = new KnapsackItem("hi", 2, 100.0, 10, 1, 0.1);
        var big = new KnapsackItem("big", 3, 300.0, 10, 1, 0.1);
        var res = solver.solve(List.of(lo, hi, big), 1000, 1, 10.0, 10_000);
        assertEquals(300.0, res.value(), 1e-9);
        assertEquals(1, res.picks().size());
    }

    @Test
    void attentionConstraintDropsSlowFlips() {
        // Attention budget 1 hour. `slow` costs 2 h, `fast` costs 0.5 h.
        var slow = new KnapsackItem("slow", 1, 1000.0, 100, 1, 2.0);
        var fast = new KnapsackItem("fast", 2, 100.0, 100, 1, 0.5);
        var res = solver.solve(List.of(slow, fast), 1000, 10, 1.0, 10_000);
        assertEquals(100.0, res.value(), 1e-9);
        assertEquals(1, res.picks().size());
        assertEquals("fast", res.picks().getFirst().id());
    }

    @Test
    void classicKnapsackChoosesOptimalCombination() {
        // Budget 10, inventory 10, attention 10 — pick 3 items that together maximise value.
        // Values 60/100/120, weights 10/20/30, budget 50 → optimal is 100+120 = 220.
        var items = List.of(
                new KnapsackItem("a", 1, 60.0, 10, 1, 0.1),
                new KnapsackItem("b", 2, 100.0, 20, 1, 0.1),
                new KnapsackItem("c", 3, 120.0, 30, 1, 0.1));
        var res = solver.solve(items, 50, 10, 10.0, 10_000);
        assertEquals(220.0, res.value(), 1e-9);
        assertEquals(2, res.picks().size());
    }

    @Test
    void zeroValueItemsAreNotPicked() {
        var zero = new KnapsackItem("zero", 1, 0.0, 10, 1, 0.1);
        var real = new KnapsackItem("real", 2, 100.0, 10, 1, 0.1);
        var res = solver.solve(List.of(zero, real), 1000, 10, 10.0, 10_000);
        assertEquals(100.0, res.value(), 1e-9);
        assertEquals(1, res.picks().size());
        assertFalse(res.picks().stream().anyMatch(p -> "zero".equals(p.id())));
    }

    @Test
    void nodeCapFallsBackToBestSoFar() {
        // Build a big-ish random-ish problem and cap nodes very tight.
        var items = new ArrayList<KnapsackItem>();
        for (int i = 0; i < 30; i++) {
            items.add(new KnapsackItem("i" + i, i, 100.0 + i, 10 + i, 1, 0.1));
        }
        var tight = solver.solve(items, 200, 30, 10.0, 5);
        var full = solver.solve(items, 200, 30, 10.0, 1_000_000);
        assertTrue(full.value() >= tight.value(), "unlimited B&B should not do worse than a node-capped run");
        assertTrue(tight.value() > 0, "even a node-capped B&B must return a feasible pick");
    }
}
