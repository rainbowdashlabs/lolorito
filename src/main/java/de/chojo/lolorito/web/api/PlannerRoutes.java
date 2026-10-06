/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.service.FilterService;
import de.chojo.lolorito.service.PlannerService;
import de.chojo.lolorito.service.UserSkillLevelService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Map;
import java.util.Set;

/**
 * {@code POST /api/v1/plan} — the joint route-basket solver.
 * {@code POST /api/v1/plan/replan} — mid-run replan; the SPA calls
 * this on every "I finished world X" click and diffs the result against
 * the tail it was showing.
 */
public class PlannerRoutes implements Routes {
    private final File config;
    private final PlannerService planner;
    private final FilterService filters;
    private final UserSkillLevelService skills;

    @Inject
    public PlannerRoutes(File config, PlannerService planner, FilterService filters, UserSkillLevelService skills) {
        this.config = config;
        this.planner = planner;
        this.filters = filters;
        this.skills = skills;
    }

    /**
     * Common home-world validation for both endpoints. Returns null if a 4xx was sent.
     */
    private static World resolveHome(Context ctx, Integer homeWorldId) {
        if (homeWorldId == null || homeWorldId <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("error", "homeWorld not set — provide one in the body or save one via /me/filter"));
            return null;
        }
        World world = Worlds.worldById(homeWorldId);
        if (world == null || world.dataCenter() == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "unknown homeWorld"));
            return null;
        }
        return world;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.post("/api/v1/plan", this::plan);
        routes.post("/api/v1/plan/replan", this::replan);
    }

    private void plan(Context ctx) {
        var saved = filters.current(SessionResolver.sessionOf(ctx).discordUserId());

        PlannerService.PlanRequest req;
        try {
            req = ctx.bodyAsClass(PlannerService.PlanRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }

        Integer homeWorldId = req.homeWorld();
        if (homeWorldId == null) homeWorldId = saved.worldId();
        World world = resolveHome(ctx, homeWorldId);
        if (world == null) return;

        int refreshHours = req.refreshHours() == null ? saved.refreshHours() : req.refreshHours();
        var params =
                PlannerService.paramsFor(config, world.id(), world.dataCenter().id(), req);
        // allowCrafts/allowDesynth=true with no stored skills fall back to
        // the ungated scan (empty map) — the explicit flag is the consent.
        // Without the flag, no synthesis for that lane at all (null).
        long userId = SessionResolver.sessionOf(ctx).discordUserId();
        var craftLevels = Boolean.TRUE.equals(req.allowCrafts()) ? skills.craftLevels(userId) : null;
        var desynthLevels = Boolean.TRUE.equals(req.allowDesynth()) ? skills.desynthLevels(userId) : null;
        ctx.json(planner.plan(params, refreshHours, craftLevels, desynthLevels));
    }

    private void replan(Context ctx) {
        var saved = filters.current(SessionResolver.sessionOf(ctx).discordUserId());

        PlannerService.ReplanRequest req;
        try {
            req = ctx.bodyAsClass(PlannerService.ReplanRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }

        Integer homeWorldId = req.homeWorld();
        if (homeWorldId == null) homeWorldId = saved.worldId();
        World world = resolveHome(ctx, homeWorldId);
        if (world == null) return;

        int refreshHours = req.refreshHours() == null ? saved.refreshHours() : req.refreshHours();
        var params =
                PlannerService.paramsFor(config, world.id(), world.dataCenter().id(), req);
        var ctxObj = new PlannerService.ReplanContext(
                req.completedWorldIds() == null ? Set.of() : req.completedWorldIds(),
                req.spentBudget() == null ? 0L : req.spentBudget(),
                req.usedInventory() == null ? 0 : req.usedInventory());
        // Same lane semantics as /plan — the replan keeps the run's lanes.
        long userId = SessionResolver.sessionOf(ctx).discordUserId();
        var craftLevels = Boolean.TRUE.equals(req.allowCrafts()) ? skills.craftLevels(userId) : null;
        var desynthLevels = Boolean.TRUE.equals(req.allowDesynth()) ? skills.desynthLevels(userId) : null;
        ctx.json(planner.replan(params, refreshHours, ctxObj, craftLevels, desynthLevels));
    }
}
