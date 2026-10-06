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
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Optional secondary sink for alerts: POST a compact JSON body to the
 * URL configured via {@code value.alertWebhookUrl}. Does nothing when the
 * URL is blank, so operators can leave it off without wiring changes.
 *
 * <p>Not registered as the primary {@link AlertDispatcher} — the module
 * fans out via {@link CompositeAlertDispatcher} so Discord DMs and
 * webhooks both fire.
 */
@Singleton
public class WebhookAlertDispatcher implements AlertDispatcher {

    private static final Logger log = getLogger(WebhookAlertDispatcher.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final File config;
    private final UserPreferencesService preferences;
    private final HttpClient http;

    @Inject
    public WebhookAlertDispatcher(File config, UserPreferencesService preferences) {
        this.config = config;
        this.preferences = preferences;
        this.http = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    }

    @Override
    public void dispatch(AlertRule rule, int observedPrice) {
        // Prefer the caller's per-user webhook when they've configured one;
        // otherwise fall back to the instance-wide URL from config.
        String url = preferences.alertWebhookUrlFor(rule.userId());
        if (url == null || url.isBlank()) url = config.value().alertWebhookUrl();
        if (url == null || url.isBlank()) return;
        String body = ("{\"ruleId\":\"%s\",\"userId\":%d,\"itemId\":%d,"
                        + "\"worldId\":%s,\"dataCenterId\":%s,\"hq\":%s,"
                        + "\"kind\":\"%s\",\"threshold\":%d,\"observedPrice\":%d}")
                .formatted(
                        rule.id(),
                        rule.userId(),
                        rule.itemId(),
                        rule.scope().worldId(),
                        rule.scope().dataCenterId(),
                        rule.hq() == null ? "null" : rule.hq(),
                        rule.kind().wire(),
                        rule.thresholdPrice(),
                        observedPrice);
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
}
