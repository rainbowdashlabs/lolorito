/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.entity.Session;
import de.chojo.lolorito.service.RetainerAttributionService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Map;

/**
 * User's declared retainers + their observed listings.
 *
 * <p>The FFXIV client no longer exposes retainer owner ids, so matching
 * is done purely by {@code (world, retainer_name)}. Users have to
 * declare each retainer they own individually; each retainer needs at
 * least one active listing before Universalis observes it.
 *
 * <ul>
 *   <li>{@code GET /api/v1/me/retainers} — declared retainers, each row
 *       carrying a {@code seen} flag we compute at read time.</li>
 *   <li>{@code POST /api/v1/me/retainers} — declare a new retainer.</li>
 *   <li>{@code DELETE /api/v1/me/retainers} — undeclare (body: worldId + name).</li>
 *   <li>{@code GET /api/v1/me/listings} — every listing we've attributed to the caller.</li>
 * </ul>
 */
public class RetainerRoutes implements Routes {

    private final RetainerAttributionService service;

    @Inject
    public RetainerRoutes(RetainerAttributionService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/me/retainers", this::list);
        routes.post("/api/v1/me/retainers", this::create);
        routes.delete("/api/v1/me/retainers", this::delete);
        routes.get("/api/v1/me/listings", this::ownedListings);
        routes.get("/api/v1/retainer-suggestions", this::suggest);
    }

    private void suggest(Context ctx) {
        Session s = requireSession(ctx);
        if (s == null) return;
        int worldId = QueryParams.intOr(ctx, "world", 0, 0, Integer.MAX_VALUE);
        if (worldId <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "world required"));
            return;
        }
        String q = ctx.queryParam("q");
        ctx.json(service.suggestRetainerNames(worldId, q == null ? "" : q));
    }

    private void list(Context ctx) {
        Session s = requireSession(ctx);
        if (s == null) return;
        ctx.json(withSeenFlag(service.list(s.discordUserId())));
    }

    /** Decorate the declared retainers with a {@code seen} boolean (has any listing yet). */
    private java.util.List<java.util.Map<String, Object>> withSeenFlag(
            java.util.List<de.chojo.lolorito.entity.CharacterRetainer> declared) {
        var out = new java.util.ArrayList<java.util.Map<String, Object>>(declared.size());
        for (var r : declared) {
            var m = new java.util.LinkedHashMap<String, Object>();
            m.put("discordUserId", r.discordUserId());
            m.put("retainerName", r.retainerName());
            m.put("worldId", r.worldId());
            m.put("createdAt", r.createdAt());
            m.put("updatedAt", r.updatedAt());
            m.put("seen", service.hasBeenSeen(r.worldId(), r.retainerName()));
            out.add(m);
        }
        return out;
    }

    private void create(Context ctx) {
        Session s = requireSession(ctx);
        if (s == null) return;
        RetainerBody body;
        try {
            body = ctx.bodyAsClass(RetainerBody.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        if (body == null || body.retainerName == null || body.retainerName.isBlank() || body.worldId <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "retainerName + worldId required"));
            return;
        }
        service.put(s.discordUserId(), body.worldId, body.retainerName.trim());
        ctx.status(HttpStatus.CREATED).json(withSeenFlag(service.list(s.discordUserId())));
    }

    private void delete(Context ctx) {
        Session s = requireSession(ctx);
        if (s == null) return;
        RetainerBody body;
        try {
            body = ctx.bodyAsClass(RetainerBody.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        if (body == null || body.retainerName == null || body.worldId <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "retainerName + worldId required"));
            return;
        }
        if (!service.remove(s.discordUserId(), body.worldId, body.retainerName.trim())) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void ownedListings(Context ctx) {
        Session s = requireSession(ctx);
        if (s == null) return;
        ctx.json(service.ownedListings(s.discordUserId()));
    }

    private static Session requireSession(Context ctx) {
        Session s = SessionResolver.sessionOf(ctx);
        if (s == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
        }
        return s;
    }

    public static final class RetainerBody {
        public String retainerName;
        public int worldId;
    }
}
