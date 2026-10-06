/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketModelFitterShrinkageTest {

    @Test
    void zeroObservationsZeroShrinkage() {
        assertEquals(0.0, MarketModelFitter.shrinkage(0, 20), 1e-9);
    }

    @Test
    void twentyObservationsAgainstPriorKTwentyPullsHalfway() {
        assertEquals(0.5, MarketModelFitter.shrinkage(20, 20), 1e-9);
    }

    @Test
    void largeObservationCountApproachesFullTrust() {
        assertEquals(1.0, MarketModelFitter.shrinkage(1_000_000, 20), 1e-4);
    }

    @Test
    void priorKZeroYieldsFullTrust() {
        // No prior → every observation is fully trusted immediately.
        assertEquals(1.0, MarketModelFitter.shrinkage(3, 0), 1e-9);
    }

    @Test
    void negativePriorKClampsToZero() {
        assertEquals(1.0, MarketModelFitter.shrinkage(5, -10), 1e-9);
    }
}
