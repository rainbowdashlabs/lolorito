/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner.ilp;

import java.util.List;

/**
 * Contract for the selection layer of the joint route-basket planner.
 * Anything that can maximise a sum of values under three
 * capacities (budget, inventory, attention) plus a mutual-exclusion group
 * per {@link KnapsackItem#group()} can slot in behind this interface.
 *
 * <p>The default implementation ({@link KnapsackSolver}) is a hand-rolled
 * branch-and-bound sized for the search space we actually have (~250
 * items × ~10 worlds × 3 actions). The escape hatch lives here: if the
 * hand-rolled solver ever OOMs or times out on real
 * fleets we drop in an OR-Tools or Choco-Solver wrapper without touching
 * the engine above.
 */
public interface Solver {

    /**
     * Solve for the maximum-value subset of {@code items} that fits inside
     * every capacity and picks at most one item per {@code group}.
     *
     * @param items                candidates — one 0/1 pick each
     * @param budget               gil cap (a picked item must fit)
     * @param inventorySlots       unit cap
     * @param attentionBudgetHours attention-hours cap
     * @param maxNodes             hard cap on internal iterations before
     *                             the solver returns the best-so-far
     * @return the picks that maximise summed value under all constraints
     */
    SolverResult solve(
            List<KnapsackItem> items, long budget, int inventorySlots, double attentionBudgetHours, int maxNodes);
}
