/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfferBoundsTest {

    @Test
    void noneKeepsTheAvailableQuantity() {
        assertEquals(40, OfferBounds.NONE.clamp(40, 1_000, 99));
    }

    @Test
    void budgetCapsByWholeUnits() {
        assertEquals(3, new OfferBounds(3_500, 0).clamp(40, 1_000, 99));
    }

    @Test
    void slotsCapByStackSize() {
        assertEquals(20, new OfferBounds(0, 2).clamp(40, 10, 10));
    }

    @Test
    void tighterBoundWins() {
        assertEquals(5, new OfferBounds(5_000, 1).clamp(40, 1_000, 99));
        assertEquals(1, new OfferBounds(1_000_000, 1).clamp(40, 10, 1));
    }

    @Test
    void zeroWhenNotEvenOneUnitFits() {
        assertEquals(0, new OfferBounds(999, 0).clamp(40, 1_000, 99));
    }

    @Test
    void unknownStackSizeCountsAsOnePerSlot() {
        assertEquals(3, new OfferBounds(0, 3).clamp(40, 10, 0));
    }
}
