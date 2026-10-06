/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.entity.AlertKind;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.repository.AlertRules;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertServiceTest {

    private static AlertService service(InMemoryRules store) {
        return new AlertService(store, (rule, price) -> {});
    }

    @Test
    void createRejectsMissingItemId() {
        var svc = service(new InMemoryRules());
        var req = new AlertService.CreateRequest(null, 66, null, false, "price_below", 100, 30);
        assertThrows(IllegalArgumentException.class, () -> svc.create(1L, req));
    }

    @Test
    void createRejectsBothScopes() {
        var svc = service(new InMemoryRules());
        var req = new AlertService.CreateRequest(1, 66, 7, false, "price_below", 100, 30);
        assertThrows(IllegalArgumentException.class, () -> svc.create(1L, req));
    }

    @Test
    void createRejectsNeitherScope() {
        var svc = service(new InMemoryRules());
        var req = new AlertService.CreateRequest(1, null, null, false, "price_below", 100, 30);
        assertThrows(IllegalArgumentException.class, () -> svc.create(1L, req));
    }

    @Test
    void createClampsCooldownAndDefaultsThreshold() {
        var store = new InMemoryRules();
        var svc = service(store);
        var req = new AlertService.CreateRequest(42, null, 7, null, null, 0, 100_000);
        var rule = svc.create(9L, req);
        assertEquals(9L, rule.userId());
        assertEquals(42, rule.itemId());
        assertEquals(AlertKind.PRICE_BELOW, rule.kind()); // default
        assertEquals(1, rule.thresholdPrice()); // clamped up from 0
        assertEquals(60 * 24 * 7, rule.cooldownMinutes()); // clamped down from 100_000
        assertTrue(rule.enabled());
        assertEquals(1, store.inserted.size());
    }

    @Test
    void createDefaultsCooldownWhenNull() {
        var svc = service(new InMemoryRules());
        var req = new AlertService.CreateRequest(1, 66, null, null, "price_above", 500, null);
        var rule = svc.create(1L, req);
        assertEquals(60, rule.cooldownMinutes());
        assertEquals(AlertKind.PRICE_ABOVE, rule.kind());
        assertEquals(500, rule.thresholdPrice());
    }

    @Test
    void findOwnFiltersToOwner() {
        var store = new InMemoryRules();
        var svc = service(store);
        var r = svc.create(1L, new AlertService.CreateRequest(1, 66, null, null, "price_below", 100, 60));
        assertTrue(svc.findOwn(r.id(), 1L).isPresent());
        assertTrue(svc.findOwn(r.id(), 2L).isEmpty());
    }

    @Test
    void deleteFailsForNonOwnerAndMissing() {
        var svc = service(new InMemoryRules());
        var r = svc.create(1L, new AlertService.CreateRequest(1, 66, null, null, "price_below", 100, 60));
        assertFalse(svc.delete(r.id(), 2L));
        assertFalse(svc.delete(UUID.randomUUID(), 1L));
        assertTrue(svc.delete(r.id(), 1L));
    }

    @Test
    void setEnabledOwnerCanToggleOthersCannot() {
        var svc = service(new InMemoryRules());
        var r = svc.create(1L, new AlertService.CreateRequest(1, 66, null, null, "price_below", 100, 60));
        assertTrue(svc.setEnabled(r.id(), 2L, false).isEmpty());
        var updated = svc.setEnabled(r.id(), 1L, false).orElseThrow();
        assertFalse(updated.enabled());
    }

    @Test
    void listByUserReturnsUserRules() {
        var svc = service(new InMemoryRules());
        svc.create(1L, new AlertService.CreateRequest(1, 66, null, null, "price_below", 100, 60));
        svc.create(1L, new AlertService.CreateRequest(2, 66, null, null, "price_below", 100, 60));
        svc.create(2L, new AlertService.CreateRequest(3, 66, null, null, "price_below", 100, 60));
        assertEquals(2, svc.listByUser(1L).size());
        assertEquals(1, svc.listByUser(2L).size());
    }

    // -- In-memory repo stub ----------------------------------------------

    private static final class InMemoryRules extends AlertRules {
        final Map<UUID, AlertRule> inserted = new HashMap<>();

        @Override
        public void insert(AlertRule rule) {
            inserted.put(rule.id(), rule);
        }

        @Override
        public Optional<AlertRule> findById(UUID id) {
            return Optional.ofNullable(inserted.get(id));
        }

        @Override
        public List<AlertRule> listByUser(long userId) {
            return inserted.values().stream().filter(r -> r.userId() == userId).toList();
        }

        @Override
        public List<AlertRule> listEnabled() {
            return inserted.values().stream().filter(AlertRule::enabled).toList();
        }

        @Override
        public boolean updateEnabled(UUID id, boolean enabled) {
            AlertRule prev = inserted.get(id);
            if (prev == null) return false;
            inserted.put(id, withEnabled(prev, enabled));
            return true;
        }

        @Override
        public void updateLastTriggered(UUID id, Instant when) {
            // no-op for tests
        }

        @Override
        public boolean delete(UUID id) {
            return inserted.remove(id) != null;
        }

        private static AlertRule withEnabled(AlertRule r, boolean enabled) {
            return new AlertRule(
                    r.id(),
                    r.userId(),
                    r.itemId(),
                    r.scope(),
                    r.hq(),
                    r.kind(),
                    r.thresholdPrice(),
                    enabled,
                    r.cooldownMinutes(),
                    r.lastTriggeredAt(),
                    r.createdAt());
        }
    }
}
