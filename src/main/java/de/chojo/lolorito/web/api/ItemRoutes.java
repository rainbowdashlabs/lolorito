/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.service.FilterService;
import de.chojo.lolorito.service.ItemDetailService;
import de.chojo.lolorito.service.UserPreferencesService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Map;

/**
 * {@code GET /api/v1/items/{id}?hq=&home_world=} — item detail: name,
 * cross-DC listings, and the home-world market model.
 */
public class ItemRoutes implements Routes {
    private final ItemDetailService service;
    private final FilterService filters;
    private final ItemDetail repo;
    private final UserPreferencesService prefs;
    private final de.chojo.lolorito.service.UserSkillLevelService skills;
    private final de.chojo.lolorito.config.file.File config;

    @Inject
    public ItemRoutes(
            ItemDetailService service,
            FilterService filters,
            ItemDetail repo,
            UserPreferencesService prefs,
            de.chojo.lolorito.service.UserSkillLevelService skills,
            de.chojo.lolorito.config.file.File config) {
        this.service = service;
        this.filters = filters;
        this.repo = repo;
        this.prefs = prefs;
        this.skills = skills;
        this.config = config;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/items/{id}", this::detail);
        routes.get("/api/v1/items/{id}/sales-history", this::salesHistory);
    }

    private void salesHistory(Context ctx) {
        int itemId;
        try {
            itemId = Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid item id"));
            return;
        }
        var session = SessionResolver.sessionOf(ctx);
        Integer homeWorldId = QueryParams.optInt(ctx, "home_world");
        if (homeWorldId == null) {
            homeWorldId = filters.current(session.discordUserId()).worldId();
        }
        if (homeWorldId == null || homeWorldId <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "home_world not set"));
            return;
        }
        boolean hq = "true".equalsIgnoreCase(ctx.queryParam("hq"));
        int days = QueryParams.intOr(ctx, "days", 7, 1, 60);
        ctx.json(repo.salesHistory(itemId, homeWorldId, hq, days));
    }

    private void detail(Context ctx) {
        int itemId;
        try {
            itemId = Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid item id"));
            return;
        }

        var session = SessionResolver.sessionOf(ctx);
        Integer homeWorldId = QueryParams.optInt(ctx, "home_world");
        if (homeWorldId == null) {
            homeWorldId = filters.current(session.discordUserId()).worldId();
        }
        if (homeWorldId <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "home_world not set"));
            return;
        }

        boolean hq = "true".equalsIgnoreCase(ctx.queryParam("hq"));

        long userId = session.discordUserId();
        // Everything viewer-specific in one context: language, craft
        // levels (gate buy-vs-craft, class AND level; empty = ungated
        // informational view), ingredient-sourcing scope from the offer
        // filter, and the user's attention fraction.
        var viewer = new ItemDetailService.ViewerContext(
                prefs.languageFor(userId),
                skills.craftLevels(userId),
                filters.current(userId).targetEnum(),
                prefs.attentionFractionFor(userId, 0.25));
        var detail = service.load(itemId, homeWorldId, hq, viewer).orElse(null);
        if (detail == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "unknown home_world"));
            return;
        }
        ctx.json(detail);
    }
}
