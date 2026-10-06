/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
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
 * Session management for the Settings tray.
 *
 * <ul>
 *   <li>{@code GET /api/v1/me/sessions} — list caller's active sessions.</li>
 *   <li>{@code DELETE /api/v1/me/sessions/{id}} — revoke one.</li>
 *   <li>{@code DELETE /api/v1/me/sessions} — nuke every session for the caller (danger zone).</li>
 * </ul>
 */
public class SessionRoutes implements Routes {

    private final Sessions sessions;

    @Inject
    public SessionRoutes(Sessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/me/sessions", this::list);
        routes.delete("/api/v1/me/sessions/{id}", this::revoke);
        routes.delete("/api/v1/me/sessions", this::revokeAll);
    }

    private void list(Context ctx) {
        Session current = SessionResolver.sessionOf(ctx);
        if (current == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        List<Map<String, Object>> rows = sessions.listByUser(current.discordUserId()).stream()
                .map(s -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", s.id());
                    m.put("createdAt", s.createdAt().toString());
                    m.put("expiresAt", s.expiresAt().toString());
                    m.put("userAgent", s.userAgent() == null ? "" : s.userAgent());
                    m.put("current", s.id().equals(current.id()));
                    return m;
                })
                .toList();
        ctx.json(rows);
    }

    private void revoke(Context ctx) {
        Session current = SessionResolver.sessionOf(ctx);
        if (current == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        String id = ctx.pathParam("id");
        // Owner check: session must belong to the caller.
        var target = sessions.find(id).orElse(null);
        if (target == null || target.discordUserId() != current.discordUserId()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        sessions.delete(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void revokeAll(Context ctx) {
        Session current = SessionResolver.sessionOf(ctx);
        if (current == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        int removed = sessions.deleteAllForUser(current.discordUserId());
        ctx.json(Map.of("removed", removed));
    }
}
