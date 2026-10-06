/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.entity.Basket;
import de.chojo.lolorito.entity.BasketItem;
import de.chojo.lolorito.entity.BasketVisibility;
import de.chojo.lolorito.service.BasketService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST for saved baskets — the "share this run" flow.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/baskets} — list caller's saved baskets.</li>
 *   <li>{@code POST /api/v1/baskets} — create a new one, returns id + shareToken.</li>
 *   <li>{@code GET/PUT/DELETE /api/v1/baskets/{id}} — owner-only.</li>
 *   <li>{@code GET  /api/v1/baskets/shared/{token}} — public share read; the
 *       route consults {@link BasketVisibility} to decide whether the caller
 *       (signed-in or not) is allowed.</li>
 * </ul>
 */
public class BasketRoutes implements Routes {

    private final BasketService baskets;

    @Inject
    public BasketRoutes(BasketService baskets) {
        this.baskets = baskets;
    }

    private static UUID parseUuid(Context ctx) {
        try {
            return UUID.fromString(ctx.pathParam("id"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid id"));
            return null;
        }
    }

    // -- Handlers ----------------------------------------------------------

    private static BasketDto toDto(Basket b) {
        return new BasketDto(
                b.id(),
                b.name(),
                b.visibility().wire(),
                b.shareToken(),
                b.createdAt(),
                b.updatedAt(),
                b.items().stream().map(BasketRoutes::fromEntity).toList());
    }

    /**
     * Same as {@link #toDto} but strips ownership-only fields — used for share reads.
     */
    private static SharedBasketDto toSharedDto(Basket b) {
        return new SharedBasketDto(
                b.name(),
                b.visibility().wire(),
                b.updatedAt(),
                b.items().stream().map(BasketRoutes::fromEntity).toList());
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/baskets", this::listOwn);
        routes.post("/api/v1/baskets", this::create);
        routes.get("/api/v1/baskets/shared/{token}", this::readShared);
        routes.get("/api/v1/baskets/{id}", this::readOwn);
        routes.put("/api/v1/baskets/{id}", this::update);
        routes.delete("/api/v1/baskets/{id}", this::delete);
    }

    private void listOwn(Context ctx) {
        long userId = SessionResolver.sessionOf(ctx).discordUserId();
        ctx.json(baskets.listOwn(userId).stream().map(BasketRoutes::toDto).toList());
    }

    private void create(Context ctx) {
        long userId = SessionResolver.sessionOf(ctx).discordUserId();
        CreateRequest req;
        try {
            req = ctx.bodyAsClass(CreateRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        var basket = baskets.create(
                userId,
                req.name,
                BasketVisibility.fromWire(req.visibility),
                req.items == null
                        ? List.of()
                        : req.items.stream().map(BasketRoutes::toEntity).toList());
        ctx.status(HttpStatus.CREATED).json(toDto(basket));
    }

    private void readOwn(Context ctx) {
        long userId = SessionResolver.sessionOf(ctx).discordUserId();
        UUID id = parseUuid(ctx);
        if (id == null) return;
        var basket = baskets.findOwn(id, userId).orElse(null);
        if (basket == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        ctx.json(toDto(basket));
    }

    // -- Helpers -----------------------------------------------------------

    private void update(Context ctx) {
        long userId = SessionResolver.sessionOf(ctx).discordUserId();
        UUID id = parseUuid(ctx);
        if (id == null) return;
        UpdateRequest req;
        try {
            req = ctx.bodyAsClass(UpdateRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        var basket = baskets.update(
                        id,
                        userId,
                        req.name,
                        req.visibility == null ? null : BasketVisibility.fromWire(req.visibility),
                        req.items == null
                                ? null
                                : req.items.stream().map(BasketRoutes::toEntity).toList())
                .orElse(null);
        if (basket == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        ctx.json(toDto(basket));
    }

    private void delete(Context ctx) {
        long userId = SessionResolver.sessionOf(ctx).discordUserId();
        UUID id = parseUuid(ctx);
        if (id == null) return;
        if (!baskets.delete(id, userId)) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void readShared(Context ctx) {
        String token = ctx.pathParam("token");
        boolean sessionPresent = SessionResolver.sessionOf(ctx) != null;
        var basket = baskets.findShared(token, sessionPresent).orElse(null);
        if (basket == null) {
            // Uniform "not found" regardless of "no such token" vs "auth required" —
            // don't leak which one it was.
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
            return;
        }
        ctx.json(toSharedDto(basket));
    }

    // -- DTOs --------------------------------------------------------------

    public record CreateRequest(String name, String visibility, List<ItemDto> items) {}

    public record UpdateRequest(String name, String visibility, List<ItemDto> items) {}

    public record BasketDto(
            UUID id,
            String name,
            String visibility,
            String shareToken,
            Instant createdAt,
            Instant updatedAt,
            List<ItemDto> items) {}

    public record SharedBasketDto(String name, String visibility, Instant updatedAt, List<ItemDto> items) {}

    /**
     * Wire shape of one basket line. Fields are boxed so Jackson can build
     * a deserializer for {@code List<ItemDto>} — the primitive form trips
     * up jackson-databind's contextual resolver on record types.
     */
    public record ItemDto(
            String key,
            Integer itemId,
            String itemName,
            Boolean hq,
            Integer sourceWorldId,
            String sourceWorldName,
            Integer quantity,
            Integer buyPrice,
            String action,
            Double evPerHour,
            Instant addedAt) {}

    private static BasketItem toEntity(ItemDto it) {
        return new BasketItem(
                it.key(),
                it.itemId() == null ? 0 : it.itemId(),
                it.itemName(),
                it.hq() != null && it.hq(),
                it.sourceWorldId() == null ? 0 : it.sourceWorldId(),
                it.sourceWorldName(),
                it.quantity() == null ? 0 : it.quantity(),
                it.buyPrice() == null ? 0 : it.buyPrice(),
                it.action(),
                it.evPerHour() == null ? 0.0 : it.evPerHour(),
                it.addedAt() == null ? Instant.now() : it.addedAt(),
                0);
    }

    private static ItemDto fromEntity(BasketItem it) {
        return new ItemDto(
                it.key(),
                it.itemId(),
                it.itemName(),
                it.hq(),
                it.sourceWorldId(),
                it.sourceWorldName(),
                it.quantity(),
                it.buyPrice(),
                it.action(),
                it.evPerHour(),
                it.addedAt());
    }
}
