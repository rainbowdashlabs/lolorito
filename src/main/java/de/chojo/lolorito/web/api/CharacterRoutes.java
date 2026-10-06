/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.entity.Session;
import de.chojo.lolorito.service.CharacterProfileService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.slf4j.Logger;

import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Lodestone-backed character profile for the calling user.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/me/character} — cached row (or 404 if none).</li>
 *   <li>{@code POST /api/v1/me/character/refresh} — re-fetch from Lodestone.</li>
 *   <li>{@code DELETE /api/v1/me/character} — unlink.</li>
 * </ul>
 */
public class CharacterRoutes implements Routes {

    private static final Logger log = getLogger(CharacterRoutes.class);

    private final CharacterProfileService service;

    @Inject
    public CharacterRoutes(CharacterProfileService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/me/character", this::get);
        routes.post("/api/v1/me/character/refresh", this::refresh);
        routes.delete("/api/v1/me/character", this::unlink);
    }

    private void get(Context ctx) {
        Session s = requireSession(ctx);
        if (s == null) return;
        var current = service.current(s.discordUserId());
        if (current.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not linked"));
            return;
        }
        var row = current.get();
        // Body wraps the raw JSON blob so the SPA can parse it as a nested object.
        ctx.contentType("application/json")
                .result("{\"lodestoneId\":" + row.lodestoneId()
                        + ",\"fetchedAt\":\"" + row.fetchedAt() + "\""
                        + ",\"stale\":" + service.isStale(row)
                        + ",\"profile\":" + row.profileJson() + "}");
    }

    private void refresh(Context ctx) {
        Session s = requireSession(ctx);
        if (s == null) return;
        RefreshRequest req;
        try {
            req = ctx.bodyAsClass(RefreshRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        long lodestoneId = req == null ? 0L : req.lodestoneId;
        if (lodestoneId <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "lodestoneId required"));
            return;
        }
        try {
            var row = service.refresh(s.discordUserId(), lodestoneId);
            ctx.contentType("application/json")
                    .result("{\"lodestoneId\":" + row.lodestoneId()
                            + ",\"fetchedAt\":\"" + row.fetchedAt() + "\""
                            + ",\"stale\":false"
                            + ",\"profile\":" + row.profileJson() + "}");
        } catch (Exception e) {
            log.warn(
                    "Lodestone refresh failed for user {} lodestoneId {}: {}",
                    s.discordUserId(),
                    lodestoneId,
                    e.getMessage());
            ctx.status(HttpStatus.BAD_GATEWAY).json(Map.of("error", "lodestone unreachable"));
        }
    }

    private void unlink(Context ctx) {
        Session s = requireSession(ctx);
        if (s == null) return;
        if (!service.unlink(s.discordUserId())) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not linked"));
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

    public static final class RefreshRequest {
        public long lodestoneId;
    }
}
