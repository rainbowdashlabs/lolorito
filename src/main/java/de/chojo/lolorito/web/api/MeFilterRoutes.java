/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.service.FilterService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Map;

/**
 * {@code GET/PUT /api/v1/me/filter} — the current user's saved offer
 * filter. Reads and updates through {@link FilterService}; body is a
 * {@link FilterService.FilterPatch} where every field is optional.
 */
public class MeFilterRoutes implements Routes {
    private final FilterService service;

    @Inject
    public MeFilterRoutes(FilterService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/me/filter", this::get);
        routes.put("/api/v1/me/filter", this::put);
        routes.delete("/api/v1/me/filter", this::reset);
    }

    private void reset(Context ctx) {
        var session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        ctx.json(service.reset(session.discordUserId()));
    }

    private void get(Context ctx) {
        var session = SessionResolver.sessionOf(ctx);
        ctx.json(service.current(session.discordUserId()));
    }

    private void put(Context ctx) {
        var session = SessionResolver.sessionOf(ctx);
        FilterService.FilterPatch patch;
        try {
            patch = ctx.bodyAsClass(FilterService.FilterPatch.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        ctx.json(service.update(session.discordUserId(), patch));
    }
}
