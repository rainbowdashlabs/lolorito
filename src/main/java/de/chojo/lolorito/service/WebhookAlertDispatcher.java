/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.universalis.provider.NameSupplier;
import org.slf4j.Logger;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Optional secondary sink for alerts: POST to the user's webhook URL, or the
 * one configured via {@code value.alertWebhookUrl}. Does nothing when both are
 * blank, so operators can leave it off without wiring changes.
 *
 * <p>A Discord webhook URL gets a Discord message with the same embed as the
 * DM, because Discord rejects a body without {@code content} or
 * {@code embeds}. Any other URL gets a compact JSON body with the raw rule
 * fields, for automation tools.
 *
 * <p>Not registered as the primary {@link AlertDispatcher} — the module
 * fans out via {@link CompositeAlertDispatcher} so Discord DMs and
 * webhooks both fire.
 */
@Singleton
public class WebhookAlertDispatcher implements AlertDispatcher {

    private static final Logger log = getLogger(WebhookAlertDispatcher.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> DISCORD_HOSTS =
            Set.of("discord.com", "discordapp.com", "ptb.discord.com", "canary.discord.com");

    private final File config;
    private final UserPreferencesService preferences;
    private final NameSupplier names;
    private final HttpClient http;

    @Inject
    public WebhookAlertDispatcher(File config, UserPreferencesService preferences, NameSupplier names) {
        this.config = config;
        this.preferences = preferences;
        this.names = names;
        this.http = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    }

    @Override
    public void dispatch(AlertRule rule, int observed) {
        // Prefer the caller's per-user webhook when they've configured one;
        // otherwise fall back to the instance-wide URL from config.
        String url = preferences.alertWebhookUrlFor(rule.userId());
        if (url == null || url.isBlank()) url = config.value().alertWebhookUrl();
        if (url == null || url.isBlank()) return;
        String body = bodyFor(url, rule, observed);
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            var res = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() >= 400) {
                log.warn("Webhook alert for rule {} returned HTTP {}", rule.id(), res.statusCode());
            }
        } catch (Exception e) {
            log.warn("Webhook alert for rule {} failed", rule.id(), e);
        }
    }

    String bodyFor(String url, AlertRule rule, int observed) {
        return isDiscordWebhook(url) ? discordBody(rule, observed) : plainBody(rule, observed);
    }

    static boolean isDiscordWebhook(String url) {
        try {
            var uri = URI.create(url.trim());
            return uri.getHost() != null
                    && DISCORD_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/api/webhooks/");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String discordBody(AlertRule rule, int observed) {
        var message = AlertMessage.of(rule, observed, names);
        var embed = new LinkedHashMap<String, Object>();
        embed.put("title", message.title());
        embed.put("description", message.description());
        embed.put(
                "fields",
                List.of(
                        Map.of("name", "Threshold", "value", message.threshold(), "inline", true),
                        Map.of("name", "HQ", "value", message.quality(), "inline", true)));
        embed.put("footer", Map.of("text", message.footer()));
        return JSON.writeValueAsString(Map.of("embeds", List.of(embed)));
    }

    private static String plainBody(AlertRule rule, int observed) {
        var body = new LinkedHashMap<String, Object>();
        body.put("ruleId", rule.id().toString());
        body.put("userId", rule.userId());
        body.put("itemId", rule.itemId());
        body.put("worldId", rule.scope().worldId());
        body.put("dataCenterId", rule.scope().dataCenterId());
        body.put("hq", rule.hq());
        body.put("kind", rule.kind().wire());
        body.put("threshold", rule.threshold());
        body.put("observed", observed);
        return JSON.writeValueAsString(body);
    }
}
