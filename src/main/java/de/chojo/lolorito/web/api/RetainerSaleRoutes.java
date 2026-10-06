/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.entity.Session;
import de.chojo.lolorito.repository.RetainerSales;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.time.Instant;
import java.util.Map;

/**
 * Own-retainer sale journal. Every endpoint is scoped to the calling
 * user's Discord id so one user never reads another user's sales.
 *
 * <ul>
 *   <li>{@code GET /api/v1/me/retainer-sales} — most-recent-first, capped at 200</li>
 *   <li>{@code POST /api/v1/me/retainer-sales} — log a new sale</li>
 *   <li>{@code DELETE /api/v1/me/retainer-sales/{id}} — remove one row</li>
 * </ul>
 */
public class RetainerSaleRoutes implements Routes {

    private final RetainerSales sales;

    @Inject
    public RetainerSaleRoutes(RetainerSales sales) {
        this.sales = sales;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/me/retainer-sales", this::list);
        routes.post("/api/v1/me/retainer-sales", this::create);
        routes.delete("/api/v1/me/retainer-sales/{id}", this::delete);
    }

    private void list(Context ctx) {
        Session session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        int limit = QueryParams.intOr(ctx, "limit", 100, 1, 200);
        ctx.json(sales.recent(session.discordUserId(), limit));
    }

    private void create(Context ctx) {
        Session session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        CreateRequest req;
        try {
            req = ctx.bodyAsClass(CreateRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        if (req == null || req.itemId == null || req.worldId == null || req.unitPrice == null || req.quantity == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "missing fields"));
            return;
        }
        if (req.unitPrice <= 0 || req.quantity <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "unit_price and quantity must be positive"));
            return;
        }
        var sold = req.soldAt == null ? Instant.now() : req.soldAt;
        var row = sales.insert(
                session.discordUserId(),
                blankToNull(req.retainerName),
                req.itemId,
                req.worldId,
                Boolean.TRUE.equals(req.hq),
                req.unitPrice,
                req.quantity,
                sold,
                blankToNull(req.note));
        ctx.status(HttpStatus.CREATED).json(row);
    }

    private void delete(Context ctx) {
        Session session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        long id;
        try {
            id = Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "bad id"));
            return;
        }
        if (!sales.delete(id, session.discordUserId())) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Client payload for {@code POST}. Boxed fields so Jackson accepts partial bodies. */
    public static final class CreateRequest {
        public String retainerName;
        public Integer itemId;
        public Integer worldId;
        public Boolean hq;
        public Long unitPrice;
        public Integer quantity;
        public Instant soldAt;
        public String note;
    }
}
