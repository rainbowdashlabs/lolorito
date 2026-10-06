/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.service.ItemSearchService;
import de.chojo.lolorito.service.UserPreferencesService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import de.chojo.universalis.entities.Language;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;

/**
 * {@code GET /api/v1/item-search?q=…&limit=…} — partial-name matches
 * against the {@link ItemSearchService}, in the caller's stored locale.
 */
public class ItemSearchRoutes implements Routes {

    private final ItemSearchService search;
    private final UserPreferencesService preferences;

    @Inject
    public ItemSearchRoutes(ItemSearchService search, UserPreferencesService preferences) {
        this.search = search;
        this.preferences = preferences;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/item-search", this::search);
        routes.get("/api/v1/item-names", this::names);
    }

    private void search(Context ctx) {
        var session = SessionResolver.sessionOf(ctx);
        var language = session == null ? Language.ENGLISH : preferences.languageFor(session.discordUserId());
        String q = ctx.queryParam("q");
        int limit = QueryParams.intOr(ctx, "limit", ItemSearchService.MAX_HITS, 1, ItemSearchService.MAX_HITS);
        ctx.json(search.search(q, language, limit));
    }

    /** Batched id → name lookup. Query: {@code ?ids=1,2,3}. */
    private void names(Context ctx) {
        var session = SessionResolver.sessionOf(ctx);
        var language = session == null ? Language.ENGLISH : preferences.languageFor(session.discordUserId());
        String raw = ctx.queryParam("ids");
        if (raw == null || raw.isBlank()) {
            ctx.json(java.util.Map.of());
            return;
        }
        var ids = new java.util.ArrayList<Integer>();
        for (String token : raw.split(",")) {
            try {
                int id = Integer.parseInt(token.trim());
                if (id > 0) ids.add(id);
            } catch (NumberFormatException ignored) {
                // Skip non-numeric tokens.
            }
        }
        ctx.json(search.namesById(ids, language));
    }
}
