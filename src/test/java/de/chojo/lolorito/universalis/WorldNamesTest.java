/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.universalis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldNamesTest {

    @Test
    void knownWorldsUseTheirName() {
        assertEquals("Odin", WorldNames.nameOf(66));
    }

    @Test
    void unknownWorldsFallBackToTheId() {
        assertEquals("9999", WorldNames.nameOf(9999));
    }
}
