/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.service.ItemCatalog;
import de.chojo.lolorito.service.ItemImageService;
import de.chojo.lolorito.web.Routes;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Map;

/**
 * Serves the frontend's item catalog + icon PNGs directly from the
 * backend. Frontend used to bundle icons.json/items.json and hit
 * xivapi.com for the images; now everything is proxied through here so
 * shipped images have zero third-party runtime dependencies.
 *
 * <ul>
 *   <li>{@code GET /api/v1/item-catalog} — id → {icon, ilvl, stackSize} map.
 *       Cached in-memory on the frontend; the response ships an aggressive
 *       {@code Cache-Control} header since the catalog only changes at boot.</li>
 *   <li>{@code GET /api/v1/item-icon/{itemId}} — the PNG. First hit fetches
 *       and caches; subsequent hits serve from disk.</li>
 * </ul>
 */
public class ItemCatalogRoutes implements Routes {

    private final ItemCatalog catalog;
    private final ItemImageService images;

    @Inject
    public ItemCatalogRoutes(ItemCatalog catalog, ItemImageService images) {
        this.catalog = catalog;
        this.images = images;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/item-catalog", this::catalog);
        routes.get("/api/v1/item-icon/{itemId}", this::icon);
    }

    private void catalog(Context ctx) {
        ctx.header("Cache-Control", "public, max-age=3600");
        ctx.json(catalog.allEntries());
    }

    private void icon(Context ctx) {
        int itemId;
        try {
            itemId = Integer.parseInt(ctx.pathParam("itemId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "bad item id"));
            return;
        }
        var bytes = images.iconBytes(itemId);
        if (bytes.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "no icon"));
            return;
        }
        ctx.header("Cache-Control", "public, max-age=31536000, immutable");
        ctx.contentType("image/png").result(bytes.get());
    }
}
