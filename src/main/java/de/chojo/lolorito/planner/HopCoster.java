/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

/**
 * Pure helper — how expensive is it, in wall-clock seconds, to hop between
 * two worlds. Same-DC hops cost {@link PlannerParams#tDcSeconds()}; cross-DC
 * but same-region hops cost {@link PlannerParams#tRegionSeconds()}. Cross-
 * region hops aren't reachable in-game — {@link #isReachable(WorldNode,
 * WorldNode)} returns {@code false} and {@link #hopSeconds} returns {@link
 * Integer#MAX_VALUE} so the solver naturally drops any candidate world set
 * that would require one.
 */
public final class HopCoster {

    private HopCoster() {}

    /**
     * True if the two worlds live in the same region — the only reachable case.
     */
    public static boolean isReachable(WorldNode from, WorldNode to) {
        return from.regionName().equalsIgnoreCase(to.regionName());
    }

    public static int hopSeconds(WorldNode from, WorldNode to, PlannerParams params) {
        if (!isReachable(from, to)) return Integer.MAX_VALUE;
        if (from.worldId() == to.worldId()) return 0;
        return from.dataCenterId() == to.dataCenterId() ? params.tDcSeconds() : params.tRegionSeconds();
    }
}
