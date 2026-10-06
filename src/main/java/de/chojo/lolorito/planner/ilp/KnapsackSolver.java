/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner.ilp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link Solver} — a compact multi-dimensional 0/1 knapsack.
 * Three capacities (budget, inventory, attention) plus a mutual-exclusion
 * group per item id. Branch-and-bound with a loose sum-of-remaining-value
 * bound; nodes are capped so a pathological input degrades to greedy
 * instead of hanging.
 *
 * <p>The solver is oblivious to what an item represents — {@link
 * KnapsackItem#id()} is opaque payload that comes back on the picks.
 */
public final class KnapsackSolver implements Solver {

    /**
     * For each mutual-exclusion group keep only the pick with the highest
     * strict-fit value density. The planner requires "single-source per
     * item"; the ILP never has to consider two of the same group.
     */
    private static List<KnapsackItem> collapseByGroup(List<KnapsackItem> items) {
        Map<Integer, KnapsackItem> best = new HashMap<>();
        for (KnapsackItem it : items) {
            if (it.value() <= 0) continue;
            var prev = best.get(it.group());
            if (prev == null || it.value() > prev.value()) best.put(it.group(), it);
        }
        return new ArrayList<>(best.values());
    }

    private static double density(KnapsackItem it, long budget, int inventorySlots, double attentionBudgetHours) {
        double b = budget > 0 ? (double) it.cost() / budget : 0.0;
        double q = inventorySlots > 0 ? (double) it.qty() / inventorySlots : 0.0;
        double a = attentionBudgetHours > 0 ? it.attentionHours() / attentionBudgetHours : 0.0;
        double weight = Math.max(1e-9, Math.max(b, Math.max(q, a)));
        return it.value() / weight;
    }

    @Override
    public SolverResult solve(
            List<KnapsackItem> items, long budget, int inventorySlots, double attentionBudgetHours, int maxNodes) {
        if (items.isEmpty()) return SolverResult.empty();
        var collapsed = collapseByGroup(items);
        var order = new ArrayList<>(collapsed);
        // Sort by value density — knapsack B&B benefits massively from a good order.
        order.sort(Comparator.comparingDouble(
                        (KnapsackItem it) -> density(it, budget, inventorySlots, attentionBudgetHours))
                .reversed());

        double[] suffixValueSum = new double[order.size() + 1];
        for (int i = order.size() - 1; i >= 0; i--) {
            suffixValueSum[i] =
                    suffixValueSum[i + 1] + Math.max(0.0, order.get(i).value());
        }

        var state = new State(order, suffixValueSum, budget, inventorySlots, attentionBudgetHours, maxNodes);
        state.branch(0, 0.0, budget, inventorySlots, attentionBudgetHours, new boolean[order.size()]);
        var picks = new ArrayList<KnapsackItem>();
        for (int i = 0; i < order.size(); i++) {
            if (state.bestPicks[i]) picks.add(order.get(i));
        }
        return new SolverResult(state.bestValue, picks, state.nodes, state.hitCap);
    }

    private static final class State {
        final List<KnapsackItem> order;
        final double[] suffixValueSum;
        final long budget;
        final int inventorySlots;
        final double attentionBudgetHours;
        final int maxNodes;
        final boolean[] bestPicks;
        double bestValue = 0.0;
        int nodes = 0;
        boolean hitCap = false;

        State(
                List<KnapsackItem> order,
                double[] suffixValueSum,
                long budget,
                int inventorySlots,
                double attentionBudgetHours,
                int maxNodes) {
            this.order = order;
            this.suffixValueSum = suffixValueSum;
            this.budget = budget;
            this.inventorySlots = inventorySlots;
            this.attentionBudgetHours = attentionBudgetHours;
            this.maxNodes = maxNodes;
            this.bestPicks = new boolean[order.size()];
        }

        void branch(int idx, double curValue, long remBudget, int remInv, double remAtt, boolean[] picks) {
            if (hitCap) return;
            // Any feasible curValue is a valid partial solution — skipping every remaining item is legal.
            // Recording eagerly means the node cap can still hand back the best-so-far instead of zero.
            if (curValue > bestValue) {
                bestValue = curValue;
                System.arraycopy(picks, 0, bestPicks, 0, picks.length);
            }
            if (++nodes > maxNodes) {
                hitCap = true;
                return;
            }
            if (idx == order.size()) return;
            // Bound: no better than curValue + sum(value_i for i >= idx).
            if (curValue + suffixValueSum[idx] <= bestValue) return;

            var it = order.get(idx);
            // Include branch first — usually finds high-value picks early which tightens the bound.
            if (it.cost() <= remBudget && it.qty() <= remInv && it.attentionHours() <= remAtt) {
                picks[idx] = true;
                branch(
                        idx + 1,
                        curValue + it.value(),
                        remBudget - it.cost(),
                        remInv - it.qty(),
                        remAtt - it.attentionHours(),
                        picks);
                picks[idx] = false;
                if (hitCap) return;
            }
            branch(idx + 1, curValue, remBudget, remInv, remAtt, picks);
        }
    }
}
