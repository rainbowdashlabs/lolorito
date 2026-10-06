/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner.ilp;

import java.util.List;

/**
 * Solver output — the picks that maximise summed value under the caller's caps.
 */
public record SolverResult(double value, List<KnapsackItem> picks, int nodesVisited, boolean hitNodeCap) {
    public static SolverResult empty() {
        return new SolverResult(0.0, List.of(), 0, false);
    }
}
