/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.entity.AlertKind;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.entity.AlertScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertMatcherTest {

    private static AlertRule rule(AlertKind kind, int threshold, int cooldown, Instant lastFired) {
        return new AlertRule(
                UUID.randomUUID(),
                1L,
                100,
                AlertScope.forWorld(66),
                null,
                kind,
                threshold,
                true,
                cooldown,
                lastFired,
                Instant.EPOCH);
    }

    @Test
    void thresholdCrossedBelow() {
        assertTrue(AlertMatcher.thresholdCrossed(AlertKind.PRICE_BELOW, 90, 100));
        assertTrue(AlertMatcher.thresholdCrossed(AlertKind.PRICE_BELOW, 100, 100));
        assertFalse(AlertMatcher.thresholdCrossed(AlertKind.PRICE_BELOW, 110, 100));
    }

    @Test
    void thresholdCrossedAbove() {
        assertTrue(AlertMatcher.thresholdCrossed(AlertKind.PRICE_ABOVE, 110, 100));
        assertTrue(AlertMatcher.thresholdCrossed(AlertKind.PRICE_ABOVE, 100, 100));
        assertFalse(AlertMatcher.thresholdCrossed(AlertKind.PRICE_ABOVE, 90, 100));
    }

    @Test
    void cooldownAllowsFireOnFirstRun() {
        assertTrue(AlertMatcher.cooldownElapsed(null, 60, Instant.now()));
    }

    @Test
    void cooldownBlocksTooSoon() {
        var last = Instant.parse("2026-07-02T00:00:00Z");
        var now = last.plusSeconds(60 * 30);
        assertFalse(AlertMatcher.cooldownElapsed(last, 60, now));
    }

    @Test
    void cooldownReleasesAtBoundary() {
        var last = Instant.parse("2026-07-02T00:00:00Z");
        var now = last.plusSeconds(60 * 60);
        assertTrue(AlertMatcher.cooldownElapsed(last, 60, now));
    }

    @Test
    void disabledRulesNeverFire() {
        var r = new AlertRule(
                UUID.randomUUID(),
                1L,
                1,
                AlertScope.forWorld(66),
                null,
                AlertKind.PRICE_BELOW,
                100,
                false,
                0,
                null,
                Instant.EPOCH);
        assertFalse(AlertMatcher.shouldFire(r, 50, Instant.now()));
    }

    @Test
    void missingOrZeroPriceIgnored() {
        var r = rule(AlertKind.PRICE_BELOW, 100, 0, null);
        assertFalse(AlertMatcher.shouldFire(r, null, Instant.now()));
        assertFalse(AlertMatcher.shouldFire(r, 0, Instant.now()));
    }

    @Test
    void firesWhenAllConditionsMet() {
        var r = rule(AlertKind.PRICE_BELOW, 100, 0, null);
        assertTrue(AlertMatcher.shouldFire(r, 90, Instant.now()));
    }

    @Test
    void negativePriorKClampsAsZeroCooldown() {
        // Even a nonsensical negative cooldown never blocks — clamps to 0.
        assertTrue(AlertMatcher.cooldownElapsed(Instant.EPOCH, -100, Instant.now()));
    }

    @Test
    void thresholdCrossedForVolumeAndCount() {
        assertTrue(AlertMatcher.thresholdCrossed(AlertKind.SALE_VOLUME_SPIKE, 250, 200));
        assertFalse(AlertMatcher.thresholdCrossed(AlertKind.SALE_VOLUME_SPIKE, 150, 200));
        assertTrue(AlertMatcher.thresholdCrossed(AlertKind.LISTING_COUNT_DROP, 0, 0));
        assertFalse(AlertMatcher.thresholdCrossed(AlertKind.LISTING_COUNT_DROP, 3, 2));
    }

    @Test
    void zeroIsAValidObservationForListingCounts() {
        var r = rule(AlertKind.LISTING_COUNT_DROP, 0, 0, null);
        assertTrue(AlertMatcher.shouldFire(r, 0, Instant.now()));
    }

    @Test
    void spikePercentUsesTheDailyAverageWithAFloor() {
        assertEquals(300, AlertMatcher.spikePercent(30, 70, 7));
        assertEquals(500, AlertMatcher.spikePercent(5, 0, 7), "a silent item uses one unit per day as baseline");
        assertEquals(0, AlertMatcher.spikePercent(0, 70, 7));
    }

    @Test
    void syntheticObservationAlwaysCrossesTheThreshold() {
        for (var kind : AlertKind.values()) {
            var r = rule(kind, 100, 0, null);
            assertTrue(AlertMatcher.shouldFire(r, AlertMatcher.syntheticObservation(r), Instant.now()), kind.name());
        }
    }
}
