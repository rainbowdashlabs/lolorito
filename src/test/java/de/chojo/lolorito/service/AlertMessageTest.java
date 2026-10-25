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

class AlertMessageTest {

    private static AlertRule rule(AlertKind kind, AlertScope scope, Boolean hq, int threshold) {
        return new AlertRule(UUID.randomUUID(), 1L, 9999, scope, hq, kind, threshold, true, 60, null, Instant.EPOCH);
    }

    @Test
    void priceBelowOnAWorld() {
        var message = AlertMessage.of(rule(AlertKind.PRICE_BELOW, AlertScope.forWorld(66), true, 500), 450, null);
        assertEquals("Market alert: 9999", message.title());
        assertEquals("Cheapest listing on **Odin** dropped to **450 gil**.", message.description());
        assertEquals("500 gil", message.threshold());
        assertEquals("high", message.quality());
    }

    @Test
    void priceAboveOnADataCenter() {
        var message = AlertMessage.of(rule(AlertKind.PRICE_ABOVE, AlertScope.forDataCenter(7), false, 500), 700, null);
        assertEquals("Cheapest listing on **Light** climbed to **700 gil**.", message.description());
        assertEquals("normal", message.quality());
    }

    @Test
    void saleVolumeSpike() {
        var message = AlertMessage.of(rule(AlertKind.SALE_VOLUME_SPIKE, AlertScope.forWorld(66), null, 200), 250, null);
        assertEquals(
                "Sales on **Odin** in the last 24 h are at **250 %** of the usual daily volume.",
                message.description());
        assertEquals("200 % of usual", message.threshold());
        assertEquals("either", message.quality());
    }

    @Test
    void listingCountDrop() {
        var message = AlertMessage.of(rule(AlertKind.LISTING_COUNT_DROP, AlertScope.forWorld(66), null, 5), 2, null);
        assertEquals("Only **2** listing(s) left on **Odin**.", message.description());
        assertEquals("5 listings", message.threshold());
    }
}
