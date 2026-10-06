/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

import de.chojo.lolorito.planner.ilp.KnapsackItem;
import de.chojo.lolorito.planner.ilp.KnapsackSolver;
import de.chojo.lolorito.planner.ilp.Solver;
import de.chojo.lolorito.planner.ilp.SolverResult;
import de.chojo.lolorito.value.Valuation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The solver escape hatch: any {@link Solver} slots into {@link
 * PlannerEngine#plan} through the caller-supplied overload. Two coverage
 * proofs — the custom solver actually runs, and one that always returns
 * "no picks" produces an empty plan.
 */
class PlannerEngineSolverSwapTest {

    private static final WorldNode HOME = new WorldNode(1, "Odin", 10, "Light", "Europe");
    private static final WorldNode SIBLING = new WorldNode(2, "Cerberus", 10, "Light", "Europe");
    // Prove the SolverResult record's dummy default doesn't break wired KnapsackItem consumers.
    @SuppressWarnings("unused")
    private static final SolverResult ignore =
            new SolverResult(0.0, List.of(new KnapsackItem("x", 1, 0.0, 0L, 0, 0.0)), 0, false);

    private static PlannerParams params() {
        return new PlannerParams(1, 10, 1_000_000, 100, 8.0, 1.0, 500, 5, 250, 15, 45, 200_000, 0, 40, 20, 0.05, 6.0);
    }

    private static Candidate candidate(String key, int itemId) {
        var v = new Valuation(200, 0, 50_000, 1.0, 50_000, 1.0);
        return Candidate.simple(
                key,
                itemId,
                "Item#" + itemId,
                false,
                SIBLING.worldId(),
                SIBLING.worldName(),
                SIBLING.dataCenterId(),
                SIBLING.dataCenterName(),
                SIBLING.regionName(),
                5,
                100,
                1,
                PlanAction.RESALE,
                v);
    }

    @Test
    void customSolverIsInvokedByPlannerEngine() {
        var invocations = new AtomicInteger();
        Solver spy = (items, budget, invSlots, attHours, maxNodes) -> {
            invocations.incrementAndGet();
            // Delegate to a fresh knapsack solver so we still get a real plan.
            return new KnapsackSolver().solve(items, budget, invSlots, attHours, maxNodes);
        };
        var c = candidate("k1", 42);
        var params = params();
        var plan = PlannerEngine.plan(HOME, List.of(c), params, spy);
        assertEquals(1, plan.stops().size(), "spy solver returned a real result, so a stop lands");
        assertTrue(invocations.get() >= 1, "PlannerEngine must call the supplied solver at least once");
    }

    @Test
    void nullPickingSolverProducesEmptyPlan() {
        Solver refuseAll = (items, budget, invSlots, attHours, maxNodes) -> SolverResult.empty();
        var c = candidate("k1", 42);
        var plan = PlannerEngine.plan(HOME, List.of(c), params(), refuseAll);
        assertTrue(plan.stops().isEmpty(), "solver that returns no picks → empty plan");
    }
}
