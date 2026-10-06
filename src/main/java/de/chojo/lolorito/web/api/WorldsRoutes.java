/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.service.WorldsService;
import de.chojo.lolorito.web.Routes;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;

/**
 * {@code GET /api/v1/worlds} — the static region / DC / world hierarchy.
 * Session-gated by {@link de.chojo.lolorito.web.auth.SessionResolver}.
 */
public class WorldsRoutes implements Routes {
    private final WorldsService service;

    @Inject
    public WorldsRoutes(WorldsService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/worlds", this::list);
    }

    private void list(Context ctx) {
        ctx.json(service.hierarchy());
    }
}
