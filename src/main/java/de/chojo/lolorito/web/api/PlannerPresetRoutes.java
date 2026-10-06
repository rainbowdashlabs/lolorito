/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.entity.Session;
import de.chojo.lolorito.repository.PlannerPresets;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Map;
import java.util.UUID;

/**
 * Named planner presets, per user.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/me/planner-presets} — list</li>
 *   <li>{@code POST /api/v1/me/planner-presets} — create or overwrite by name</li>
 *   <li>{@code PUT  /api/v1/me/planner-presets/{id}} — rename</li>
 *   <li>{@code DELETE /api/v1/me/planner-presets/{id}} — remove</li>
 * </ul>
 */
public class PlannerPresetRoutes implements Routes {

    /** Cap on the preset body — the form is a couple dozen scalars. */
    private static final int MAX_BODY_BYTES = 8192;

    private final PlannerPresets presets;

    @Inject
    public PlannerPresetRoutes(PlannerPresets presets) {
        this.presets = presets;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/me/planner-presets", this::list);
        routes.post("/api/v1/me/planner-presets", this::save);
        routes.put("/api/v1/me/planner-presets/{id}", this::rename);
        routes.delete("/api/v1/me/planner-presets/{id}", this::delete);
    }

    private void list(Context ctx) {
        Session session = requireSession(ctx);
        if (session == null) return;
        ctx.json(presets.list(session.discordUserId()));
    }

    private void save(Context ctx) {
        Session session = requireSession(ctx);
        if (session == null) return;
        SaveRequest req;
        try {
            req = ctx.bodyAsClass(SaveRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        String name = req == null ? null : req.name;
        if (name == null || name.isBlank() || name.length() > 60) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "name required, ≤ 60 chars"));
            return;
        }
        String paramsJson = req.params == null ? "{}" : req.params;
        if (paramsJson.length() > MAX_BODY_BYTES) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "params too large"));
            return;
        }
        var saved = presets.upsertByName(session.discordUserId(), name.strip(), paramsJson);
        ctx.status(HttpStatus.CREATED).json(saved);
    }

    private void rename(Context ctx) {
        Session session = requireSession(ctx);
        if (session == null) return;
        UUID id = parseId(ctx);
        if (id == null) return;
        RenameRequest req;
        try {
            req = ctx.bodyAsClass(RenameRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        String newName = req == null ? null : req.name;
        if (newName == null || newName.isBlank() || newName.length() > 60) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "name required, ≤ 60 chars"));
            return;
        }
        if (presets.find(id, session.discordUserId()).isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        ctx.json(presets.rename(id, session.discordUserId(), newName.strip()));
    }

    private void delete(Context ctx) {
        Session session = requireSession(ctx);
        if (session == null) return;
        UUID id = parseId(ctx);
        if (id == null) return;
        if (!presets.delete(id, session.discordUserId())) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private static Session requireSession(Context ctx) {
        Session s = SessionResolver.sessionOf(ctx);
        if (s == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
        }
        return s;
    }

    private static UUID parseId(Context ctx) {
        try {
            return UUID.fromString(ctx.pathParam("id"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "bad id"));
            return null;
        }
    }

    /** Client payload for {@code POST}. */
    public static final class SaveRequest {
        public String name;
        /** Raw JSON blob — the SPA sends the current planner form as-is. */
        public String params;
    }

    /** Client payload for {@code PUT} (rename only). */
    public static final class RenameRequest {
        public String name;
    }
}
