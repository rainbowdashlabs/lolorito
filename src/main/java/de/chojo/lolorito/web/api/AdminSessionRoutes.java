/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.entity.Session;
import de.chojo.lolorito.repository.Sessions;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.List;
import java.util.Map;

/**
 * Ops surface for session inspection + force-evict. Every route is
 * gated on the caller being in the {@code admins} list of the config
 * file; anyone else gets a 403 without leaking that the route exists.
 *
 * <ul>
 *   <li>{@code GET /api/v1/admin/sessions?discordUserId=…} — list every
 *       session for a specific user.</li>
 *   <li>{@code DELETE /api/v1/admin/sessions/user/{id}} — evict every session for that user.</li>
 *   <li>{@code DELETE /api/v1/admin/sessions/{id}} — evict one specific session.</li>
 * </ul>
 */
public class AdminSessionRoutes implements Routes {

    private final Sessions sessions;
    private final File config;

    @Inject
    public AdminSessionRoutes(Sessions sessions, File config) {
        this.sessions = sessions;
        this.config = config;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/admin/sessions", this::list);
        routes.delete("/api/v1/admin/sessions/user/{id}", this::deleteAllForUser);
        routes.delete("/api/v1/admin/sessions/{id}", this::deleteOne);
    }

    private void list(Context ctx) {
        if (!requireAdmin(ctx)) return;
        long targetId;
        try {
            targetId = Long.parseLong(
                    ctx.queryParamAsClass("discordUserId", String.class).get());
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "discordUserId required"));
            return;
        }
        List<Map<String, Object>> rows = sessions.listByUser(targetId).stream()
                .map(AdminSessionRoutes::redact)
                .toList();
        ctx.json(rows);
    }

    private void deleteAllForUser(Context ctx) {
        if (!requireAdmin(ctx)) return;
        long targetId;
        try {
            targetId = Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "bad user id"));
            return;
        }
        int removed = sessions.deleteAllForUser(targetId);
        ctx.json(Map.of("removed", removed));
    }

    private void deleteOne(Context ctx) {
        if (!requireAdmin(ctx)) return;
        String id = ctx.pathParam("id");
        if (sessions.find(id).isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        sessions.delete(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * @return true when the caller is authenticated AND in the {@code botOwner}
     *   list of the config file, otherwise writes an error response and returns
     *   false. Ownership doubles as admin because the ops surface only has one
     *   privileged tier today.
     */
    private boolean requireAdmin(Context ctx) {
        Session current = SessionResolver.sessionOf(ctx);
        if (current == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return false;
        }
        if (!config.baseSettings().isOwner(current.discordUserId())) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("error", "forbidden"));
            return false;
        }
        return true;
    }

    /** Never leak encrypted tokens over the wire — even to admins. */
    private static Map<String, Object> redact(Session s) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", s.id());
        m.put("discordUserId", s.discordUserId());
        m.put("createdAt", s.createdAt().toString());
        m.put("expiresAt", s.expiresAt().toString());
        m.put("userAgent", s.userAgent() == null ? "" : s.userAgent());
        m.put("membershipOk", s.membershipOk());
        return m;
    }
}
