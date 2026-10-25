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
import de.chojo.universalis.entities.Name;
import de.chojo.universalis.provider.NameSupplier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookAlertDispatcherTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final NameSupplier NAMES = new NameSupplier() {
        @Override
        public Map<Integer, Name> ids() {
            return Map.of(100, new Name("Iron Ore", "Eisenerz", "", ""));
        }

        @Override
        public Map<String, Integer> en() {
            return Map.of();
        }

        @Override
        public Map<String, Integer> de() {
            return Map.of();
        }

        @Override
        public Map<String, Integer> fr() {
            return Map.of();
        }

        @Override
        public Map<String, Integer> jp() {
            return Map.of();
        }
    };

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
        var dispatcher = new WebhookAlertDispatcher(new File(), stubPrefs(), NAMES);
        assertDoesNotThrow(() -> dispatcher.dispatch(rule(), 250));
    }

    @Test
    void unreachableUrlLogsButDoesNotThrow() {
        var config = new File();
        setField(config.value(), "alertWebhookUrl", "http://127.0.0.1:1/nope");
        var dispatcher = new WebhookAlertDispatcher(config, stubPrefs(), NAMES);
        // Errors are logged, not propagated — a broken webhook never blocks the loop.
        assertDoesNotThrow(() -> dispatcher.dispatch(rule(), 250));
    }

    @Test
    void discordWebhookUrlsAreRecognised() {
        assertTrue(WebhookAlertDispatcher.isDiscordWebhook("https://discord.com/api/webhooks/1/abc"));
        assertTrue(WebhookAlertDispatcher.isDiscordWebhook("https://canary.discord.com/api/webhooks/1/abc"));
        assertTrue(WebhookAlertDispatcher.isDiscordWebhook("https://discordapp.com/api/webhooks/1/abc"));
        assertFalse(WebhookAlertDispatcher.isDiscordWebhook("https://example.com/api/webhooks/1/abc"));
        assertFalse(WebhookAlertDispatcher.isDiscordWebhook("https://discord.com/channels/1"));
        assertFalse(WebhookAlertDispatcher.isDiscordWebhook("not a url"));
    }

    @Test
    void discordWebhooksGetAnEmbed() {
        var dispatcher = new WebhookAlertDispatcher(new File(), stubPrefs(), NAMES);
        var body = JSON.readTree(dispatcher.bodyFor("https://discord.com/api/webhooks/1/abc", rule(), 250));
        var embed = body.get("embeds").get(0);
        assertEquals("Market alert: Iron Ore", embed.get("title").asString());
        assertEquals(
                "Cheapest listing on **Odin** dropped to **250 gil**.",
                embed.get("description").asString());
        assertEquals("500 gil", embed.get("fields").get(0).get("value").asString());
        assertEquals("either", embed.get("fields").get(1).get("value").asString());
    }

    @Test
    void otherUrlsGetThePlainBody() {
        var rule = rule();
        var dispatcher = new WebhookAlertDispatcher(new File(), stubPrefs(), NAMES);
        var body = JSON.readTree(dispatcher.bodyFor("https://example.com/hook", rule, 250));
        assertEquals(rule.id().toString(), body.get("ruleId").asString());
        assertEquals(66, body.get("worldId").asInt());
        assertTrue(body.get("dataCenterId").isNull());
        assertEquals(250, body.get("observed").asInt());
        assertFalse(body.has("embeds"));
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
