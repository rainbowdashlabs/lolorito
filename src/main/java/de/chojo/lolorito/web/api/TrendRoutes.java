/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.service.FilterService;
import de.chojo.lolorito.service.TrendService;
import de.chojo.lolorito.service.UserPreferencesService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Map;

/**
 * {@code GET /api/v1/trends?home_world=&window=&limit=} — items gaining /
 * losing sales momentum on the home world, with a next-24h unit forecast
 * from a per-key linear regression over daily sale buckets.
 */
public class TrendRoutes implements Routes {

    private final TrendService trends;
    private final FilterService filters;
    private final UserPreferencesService prefs;

    @Inject
    public TrendRoutes(TrendService trends, FilterService filters, UserPreferencesService prefs) {
        this.trends = trends;
        this.filters = filters;
        this.prefs = prefs;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/trends", this::board);
        routes.get("/api/v1/items/{id}/trend", this::itemTrend);
        routes.get("/api/v1/worlds/{id}/sales/24h", this::worldActivity);
    }

    /** Hourly sales on one world over the last 24 hours. */
    private void worldActivity(Context ctx) {
        var world = parseWorld(ctx.pathParam("id"));
        if (world == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "unknown world"));
            return;
        }
        ctx.json(trends.last24h(world.id()));
    }

    private static World parseWorld(String raw) {
        try {
            return knownWorld(Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The world for {@code id}, or null when the id is missing or unknown.
     * {@link Worlds#worldById} never returns null; an unknown id comes back
     * without a data center.
     */
    private static World knownWorld(Integer id) {
        if (id == null || id <= 0) return null;
        var world = Worlds.worldById(id);
        return world == null || world.dataCenter() == null ? null : world;
    }

    /** Single-key trend for the item page. 204 when the item has no sales in the window. */
    private void itemTrend(Context ctx) {
        var session = SessionResolver.sessionOf(ctx);
        int itemId;
        try {
            itemId = Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid item id"));
            return;
        }
        Integer homeWorldId = QueryParams.optInt(ctx, "home_world");
        if (homeWorldId == null)
            homeWorldId = filters.current(session.discordUserId()).worldId();
        var world = knownWorld(homeWorldId);
        if (world == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "home_world not set or unknown"));
            return;
        }
        boolean hq = "true".equalsIgnoreCase(ctx.queryParam("hq"));
        int window = QueryParams.intOr(ctx, "window", 7, 3, 14);
        var language = prefs.languageFor(session.discordUserId());
        var row = trends.forItem(world.id(), itemId, hq, window, language).orElse(null);
        if (row == null) {
            ctx.status(HttpStatus.NO_CONTENT);
            return;
        }
        ctx.json(row);
    }

    private void board(Context ctx) {
        var session = SessionResolver.sessionOf(ctx);
        Integer homeWorldId = QueryParams.optInt(ctx, "home_world");
        if (homeWorldId == null)
            homeWorldId = filters.current(session.discordUserId()).worldId();
        if (homeWorldId == null || homeWorldId <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("error", "home_world not set — provide ?home_world= or save one via /me/filter"));
            return;
        }
        var world = knownWorld(homeWorldId);
        if (world == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "unknown home_world"));
            return;
        }
        int window = QueryParams.intOr(ctx, "window", 7, 3, 14);
        int limit = QueryParams.intOr(ctx, "limit", 10, 1, 50);
        var language = prefs.languageFor(session.discordUserId());
        ctx.json(trends.board(world.id(), window, limit, language));
    }
}
