/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

/**
 * Just enough shape for the hop coster to reason about world topology.
 * {@code regionName} is the third layer of the topology: cross-region hops
 * aren't reachable in-game, so {@link HopCoster} refuses them.
 */
public record WorldNode(int worldId, String worldName, int dataCenterId, String dataCenterName, String regionName) {

    /**
     * Convenience factory for tests and small utilities — sets a default
     * region name so callers that don't care don't need to thread one
     * through. Production callers should always pass the real region.
     */
    public static WorldNode ofSingleRegion(int worldId, String worldName, int dataCenterId, String dataCenterName) {
        return new WorldNode(worldId, worldName, dataCenterId, dataCenterName, "Europe");
    }
}
