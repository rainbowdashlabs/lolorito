/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HopCosterTest {

    private static final PlannerParams P =
            new PlannerParams(0, 0, 0L, 0, 0.0, 0.0, 0, 0, 0, 15, 45, 0, 0, 40, 20, 0.05, 6.0);

    private static final WorldNode LIGHT_A = new WorldNode(1, "Odin", 10, "Light", "Europe");
    private static final WorldNode LIGHT_B = new WorldNode(2, "Alpha", 10, "Light", "Europe");
    private static final WorldNode CHAOS_A = new WorldNode(3, "Ragnarok", 20, "Chaos", "Europe");
    private static final WorldNode AETHER_A = new WorldNode(4, "Jenova", 40, "Aether", "North-America");

    @Test
    void sameWorldIsFree() {
        assertEquals(0, HopCoster.hopSeconds(LIGHT_A, LIGHT_A, P));
    }

    @Test
    void sameDcHopUsesTDc() {
        assertEquals(15, HopCoster.hopSeconds(LIGHT_A, LIGHT_B, P));
    }

    @Test
    void crossDcSameRegionHopUsesTRegion() {
        assertEquals(45, HopCoster.hopSeconds(LIGHT_A, CHAOS_A, P));
    }

    @Test
    void crossRegionIsUnreachable() {
        assertFalse(HopCoster.isReachable(LIGHT_A, AETHER_A));
        assertEquals(Integer.MAX_VALUE, HopCoster.hopSeconds(LIGHT_A, AETHER_A, P));
    }

    @Test
    void regionCheckIsCaseInsensitive() {
        var caps = new WorldNode(5, "Foo", 10, "Light", "EUROPE");
        assertTrue(HopCoster.isReachable(LIGHT_A, caps));
    }
}
