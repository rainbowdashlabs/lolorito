/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import de.chojo.lolorito.entity.AlertKind;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.entity.AlertScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRulesIntegrationTest extends RepositoryTestBase {

    private AlertRules repo;

    @BeforeEach
    void setUp() {
        repo = new AlertRules();
        query("DELETE FROM alert_rule").single(call()).delete();
    }

    private static AlertRule rule(long userId, AlertScope scope, Boolean hq) {
        var now = Instant.now();
        return new AlertRule(
                UUID.randomUUID(), userId, 100, scope, hq, AlertKind.PRICE_BELOW, 500, true, 30, null, now);
    }

    @Test
    void insertAndReadRoundTripsWorldScope() {
        var r = rule(1L, AlertScope.forWorld(66), false);
        repo.insert(r);
        var loaded = repo.findById(r.id()).orElseThrow();
        assertEquals(66, loaded.scope().worldId());
        assertNull(loaded.scope().dataCenterId());
        assertEquals(false, loaded.hq());
    }

    @Test
    void storesTheVolumeAndListingCountKinds() {
        var now = Instant.now();
        var spike = new AlertRule(
                UUID.randomUUID(),
                1L,
                100,
                AlertScope.forWorld(66),
                null,
                AlertKind.SALE_VOLUME_SPIKE,
                250,
                true,
                30,
                null,
                now);
        var soldOut = new AlertRule(
                UUID.randomUUID(),
                1L,
                100,
                AlertScope.forWorld(66),
                null,
                AlertKind.LISTING_COUNT_DROP,
                0,
                true,
                30,
                null,
                now);
        repo.insert(spike);
        repo.insert(soldOut);
        assertEquals(
                AlertKind.SALE_VOLUME_SPIKE,
                repo.findById(spike.id()).orElseThrow().kind());
        var loaded = repo.findById(soldOut.id()).orElseThrow();
        assertEquals(AlertKind.LISTING_COUNT_DROP, loaded.kind());
        assertEquals(0, loaded.threshold());
    }

    @Test
    void insertAndReadRoundTripsDataCenterScope() {
        var r = rule(2L, AlertScope.forDataCenter(7), null);
        repo.insert(r);
        var loaded = repo.findById(r.id()).orElseThrow();
        assertEquals(7, loaded.scope().dataCenterId());
        assertNull(loaded.scope().worldId());
        assertNull(loaded.hq());
    }

    @Test
    void listByUserReturnsNewestFirst() throws InterruptedException {
        var a = rule(3L, AlertScope.forWorld(66), false);
        repo.insert(a);
        Thread.sleep(15);
        var b = rule(3L, AlertScope.forWorld(66), false);
        repo.insert(b);
        var list = repo.listByUser(3L);
        assertEquals(2, list.size());
        assertEquals(b.id(), list.getFirst().id());
    }

    @Test
    void listEnabledExcludesDisabled() {
        var enabled = rule(4L, AlertScope.forWorld(66), false);
        var disabled = new AlertRule(
                UUID.randomUUID(),
                4L,
                100,
                AlertScope.forWorld(66),
                false,
                AlertKind.PRICE_BELOW,
                500,
                false,
                30,
                null,
                Instant.now());
        repo.insert(enabled);
        repo.insert(disabled);
        var open = repo.listEnabled();
        assertEquals(1, open.size());
        assertEquals(enabled.id(), open.getFirst().id());
    }

    @Test
    void updateEnabledFlipsFlag() {
        var r = rule(5L, AlertScope.forWorld(66), false);
        repo.insert(r);
        assertTrue(repo.updateEnabled(r.id(), false));
        assertFalse(repo.findById(r.id()).orElseThrow().enabled());
    }

    @Test
    void updateEnabledReturnsFalseWhenMissing() {
        assertFalse(repo.updateEnabled(UUID.randomUUID(), false));
    }

    @Test
    void updateLastTriggeredPersists() {
        var r = rule(6L, AlertScope.forWorld(66), false);
        repo.insert(r);
        var when = Instant.parse("2026-07-02T10:00:00Z");
        repo.updateLastTriggered(r.id(), when);
        var loaded = repo.findById(r.id()).orElseThrow();
        assertNotNull(loaded.lastTriggeredAt());
        assertEquals(when, loaded.lastTriggeredAt());
    }

    @Test
    void deleteRemovesRow() {
        var r = rule(7L, AlertScope.forWorld(66), false);
        repo.insert(r);
        assertTrue(repo.delete(r.id()));
        assertTrue(repo.findById(r.id()).isEmpty());
    }

    @Test
    void deleteReturnsFalseWhenMissing() {
        assertFalse(repo.delete(UUID.randomUUID()));
    }
}
