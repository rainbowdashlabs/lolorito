/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.config;

import de.chojo.lolorito.config.file.elements.Universalis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniversalisConfigTest {

    @Test
    void defaultRegionsCoverAllFiveUniversalisRegions() {
        var u = new Universalis();
        assertEquals(
                List.of("Europe", "North-America", "Oceania", "Japan", "中国"),
                u.regions(),
                "default list must match Universalis' region ids so no lookups miss");
    }
}
