/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.service.FilterService;
import de.chojo.lolorito.service.ShoppingService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@code POST /api/v1/shopping} — quick shopping for a craft: cheapest
 * ingredient run for N finished items, with per-node buy/craft overrides
 * and per-item HQ marks. Recomputed on every toggle; cheap enough that
 * no cache is needed (one recipe, two book lookups).
 */
public class ShoppingRoutes implements Routes {

    private final ShoppingService shopping;
    private final FilterService filters;

    @Inject
    public ShoppingRoutes(ShoppingService shopping, FilterService filters) {
        this.shopping = shopping;
        this.filters = filters;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.post("/api/v1/shopping", this::plan);
    }

    /** POST body — {@code itemId} required, everything else optional. */
    public record ShoppingRequest(
            Integer itemId,
            Integer recipeId,
            Integer count,
            Integer homeWorld,
            Set<Integer> hqItemIds,
            Map<Integer, String> overrides) {}

    private void plan(Context ctx) {
        ShoppingRequest req;
        try {
            req = ctx.bodyAsClass(ShoppingRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        if (req.itemId() == null || req.itemId() <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "itemId required"));
            return;
        }
        Integer homeWorld = req.homeWorld();
        if (homeWorld == null || homeWorld <= 0) {
            homeWorld = filters.current(SessionResolver.sessionOf(ctx).discordUserId())
                    .worldId();
        }
        if (homeWorld == null || homeWorld <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("error", "homeWorld not set — provide one in the body or save one via /me/filter"));
            return;
        }
        int count = req.count() == null ? 1 : Math.clamp(req.count(), 1, 9_999);
        var overrides = new HashMap<Integer, ShoppingService.Decision>();
        if (req.overrides() != null) {
            for (var e : req.overrides().entrySet()) {
                try {
                    overrides.put(
                            e.getKey(),
                            ShoppingService.Decision.valueOf(e.getValue().toUpperCase()));
                } catch (IllegalArgumentException ex) {
                    ctx.status(HttpStatus.BAD_REQUEST)
                            .json(Map.of("error", "override must be BUY or CRAFT: " + e.getValue()));
                    return;
                }
            }
        }
        var plan = shopping.plan(
                homeWorld,
                req.itemId(),
                req.recipeId(),
                count,
                req.hqItemIds() == null ? Set.of() : req.hqItemIds(),
                overrides);
        if (plan.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "no recipe for this item"));
            return;
        }
        ctx.json(plan.get());
    }
}
