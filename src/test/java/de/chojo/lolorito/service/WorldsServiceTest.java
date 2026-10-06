/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldsServiceTest {

    @Test
    void hierarchyReturnsEveryRegion() {
        var out = new WorldsService().hierarchy();
        assertFalse(out.isEmpty());
        assertTrue(out.stream().anyMatch(r -> r.name().equals("Europe")));
    }

    @Test
    void hierarchyIncludesLightDcOnEurope() {
        var out = new WorldsService().hierarchy();
        var europe =
                out.stream().filter(r -> r.name().equals("Europe")).findFirst().orElseThrow();
        assertTrue(europe.dataCenters().stream().anyMatch(dc -> dc.name().equals("Light")));
        var light = europe.dataCenters().stream()
                .filter(dc -> dc.name().equals("Light"))
                .findFirst()
                .orElseThrow();
        assertTrue(light.worlds().stream().anyMatch(w -> w.name().equals("Odin")));
        assertTrue(light.worlds().stream().anyMatch(w -> w.name().equals("Alpha")));
    }
}
