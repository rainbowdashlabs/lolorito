/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.entity.AlertKind;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.entity.AlertScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class WebhookAlertDispatcherTest {

    private static AlertRule rule() {
        return new AlertRule(
                UUID.randomUUID(),
                1L,
                100,
                AlertScope.forWorld(66),
                null,
                AlertKind.PRICE_BELOW,
                500,
                true,
                60,
                null,
                Instant.EPOCH);
    }

    @Test
    void emptyUrlIsANoop() {
        var dispatcher = new WebhookAlertDispatcher(new File(), stubPrefs());
        assertDoesNotThrow(() -> dispatcher.dispatch(rule(), 250));
    }

    @Test
    void unreachableUrlLogsButDoesNotThrow() {
        var config = new File();
        setField(config.value(), "alertWebhookUrl", "http://127.0.0.1:1/nope");
        var dispatcher = new WebhookAlertDispatcher(config, stubPrefs());
        // Errors are logged, not propagated — a broken webhook never blocks the loop.
        assertDoesNotThrow(() -> dispatcher.dispatch(rule(), 250));
    }

    /** Prefs stub with no stored webhook — falls back to config, which is what the two dispatch tests exercise. */
    private static UserPreferencesService stubPrefs() {
        return new UserPreferencesService(new de.chojo.lolorito.repository.UserPreferencesRepo()) {
            @Override
            public String alertWebhookUrlFor(long userId) {
                return null;
            }
        };
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
