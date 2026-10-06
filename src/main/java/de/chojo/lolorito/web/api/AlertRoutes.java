/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.service.AlertService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * REST for the alert rules feature.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/alerts} — list caller's rules.</li>
 *   <li>{@code POST /api/v1/alerts} — create.</li>
 *   <li>{@code DELETE /api/v1/alerts/{id}} — owner-only delete.</li>
 *   <li>{@code PATCH  /api/v1/alerts/{id}} — owner-only enable/disable toggle.</li>
 * </ul>
 */
public class AlertRoutes implements Routes {

    private final AlertService alerts;

    @Inject
    public AlertRoutes(AlertService alerts) {
        this.alerts = alerts;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/alerts", this::listOwn);
        routes.post("/api/v1/alerts", this::create);
        routes.delete("/api/v1/alerts/{id}", this::delete);
        routes.patch("/api/v1/alerts/{id}", this::patch);
        routes.post("/api/v1/alerts/{id}/test", this::test);
    }

    private void test(Context ctx) {
        long userId = SessionResolver.sessionOf(ctx).discordUserId();
        UUID id;
        try {
            id = UUID.fromString(ctx.pathParam("id"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "bad id"));
            return;
        }
        if (!alerts.test(id, userId)) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listOwn(Context ctx) {
        long userId = SessionResolver.sessionOf(ctx).discordUserId();
        ctx.json(alerts.listByUser(userId).stream().map(AlertRoutes::toDto).toList());
    }

    private void create(Context ctx) {
        long userId = SessionResolver.sessionOf(ctx).discordUserId();
        AlertService.CreateRequest req;
        try {
            req = ctx.bodyAsClass(AlertService.CreateRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        try {
            var rule = alerts.create(userId, req);
            ctx.status(HttpStatus.CREATED).json(toDto(rule));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", e.getMessage()));
        }
    }

    private void delete(Context ctx) {
        long userId = SessionResolver.sessionOf(ctx).discordUserId();
        UUID id = parseUuid(ctx);
        if (id == null) return;
        if (!alerts.delete(id, userId)) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void patch(Context ctx) {
        long userId = SessionResolver.sessionOf(ctx).discordUserId();
        UUID id = parseUuid(ctx);
        if (id == null) return;
        PatchRequest req;
        try {
            req = ctx.bodyAsClass(PatchRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        if (req.enabled == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "enabled is required"));
            return;
        }
        var rule = alerts.setEnabled(id, userId, req.enabled).orElse(null);
        if (rule == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        ctx.json(toDto(rule));
    }

    // -- Helpers -----------------------------------------------------------

    private static UUID parseUuid(Context ctx) {
        try {
            return UUID.fromString(ctx.pathParam("id"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid id"));
            return null;
        }
    }

    private static AlertDto toDto(AlertRule r) {
        return new AlertDto(
                r.id(),
                r.itemId(),
                r.scope().worldId(),
                r.scope().dataCenterId(),
                r.hq(),
                r.kind().wire(),
                r.thresholdPrice(),
                r.enabled(),
                r.cooldownMinutes(),
                r.lastTriggeredAt(),
                r.createdAt());
    }

    // -- DTOs --------------------------------------------------------------

    public record PatchRequest(Boolean enabled) {}

    public record AlertDto(
            UUID id,
            Integer itemId,
            Integer worldId,
            Integer dataCenterId,
            Boolean hq,
            String kind,
            Integer thresholdPrice,
            Boolean enabled,
            Integer cooldownMinutes,
            Instant lastTriggeredAt,
            Instant createdAt) {}
}
