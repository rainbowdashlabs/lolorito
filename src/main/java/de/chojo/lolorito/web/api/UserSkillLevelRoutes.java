/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.entity.SkillKind;
import de.chojo.lolorito.service.UserSkillLevelService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Map;

/**
 * User's per-class skill levels for both crafting and desynth.
 *
 * <ul>
 *   <li>{@code GET /api/v1/me/skills} — fully-normalised map with all eight
 *       canonical classes present in both kinds; missing rows come back as
 *       {@code level=0} (not tracked).</li>
 *   <li>{@code PUT /api/v1/me/skills} — body {@code {kind, className, level}}
 *       upserts one row. {@code level=0} deletes it.</li>
 * </ul>
 */
public class UserSkillLevelRoutes implements Routes {

    private final UserSkillLevelService service;

    @Inject
    public UserSkillLevelRoutes(UserSkillLevelService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/me/skills", this::list);
        routes.put("/api/v1/me/skills", this::put);
        routes.get("/api/v1/skills/limits", this::limits);
    }

    /**
     * Dynamic level HINTS, not limits — nothing is enforced (an enforced
     * cap breaks at the next expansion). Desynth = highest item level in
     * the catalog; the SPA's "Max" button uses it. Craft = current job
     * cap as far as we can tell, for display only.
     */
    private void limits(Context ctx) {
        ctx.json(Map.of("craftMax", 100, "desynthMax", service.desynthLevelHint()));
    }

    private void list(Context ctx) {
        var session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        // Wire shape: {"craft": {"carpenter": 90, …}, "desynth": {"blacksmith": 60, …}}
        var normalised = service.normalized(session.discordUserId());
        var out = new java.util.LinkedHashMap<String, Map<String, Integer>>();
        normalised.forEach((k, v) -> out.put(k.wire(), v));
        ctx.json(out);
    }

    private void put(Context ctx) {
        var session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        SkillBody body;
        try {
            body = ctx.bodyAsClass(SkillBody.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        SkillKind kind;
        try {
            kind = SkillKind.fromWire(body.kind);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", e.getMessage()));
            return;
        }
        try {
            service.put(session.discordUserId(), kind, body.className, body.level);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", e.getMessage()));
            return;
        }
        list(ctx);
    }

    public static final class SkillBody {
        public String kind;
        public String className;
        public int level;
    }
}
