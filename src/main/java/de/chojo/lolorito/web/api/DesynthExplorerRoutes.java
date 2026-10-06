/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.repository.OfferFilters;
import de.chojo.lolorito.service.DesynthExplorerService;
import de.chojo.lolorito.service.UserPreferencesService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code GET /api/v1/desynth?homeWorld=&limit=&class=carpenter,armorer&minLevel=40}
 * — desynth candidates ranked by expected EV/hour on the caller's home
 * world. Falls back to the saved filter's home world when the query
 * param is missing. Optional filters:
 * <ul>
 *   <li>{@code class} — comma-separated crafter class list; empty →
 *       every class the caller qualifies for.</li>
 *   <li>{@code minLevel} — minimum desynth level; 0 → no lower
 *       bound. Rows with unknown level pass unconditionally.</li>
 * </ul>
 */
public class DesynthExplorerRoutes implements Routes {

    private static final int DEFAULT_LIMIT = 50;

    private final DesynthExplorerService service;
    private final OfferFilters filters;
    private final UserPreferencesService prefs;

    @Inject
    public DesynthExplorerRoutes(DesynthExplorerService service, OfferFilters filters, UserPreferencesService prefs) {
        this.service = service;
        this.filters = filters;
        this.prefs = prefs;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/desynth", this::list);
    }

    private void list(Context ctx) {
        var session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        int homeWorld = QueryParams.intOr(ctx, "homeWorld", 0, 0, Integer.MAX_VALUE);
        if (homeWorld <= 0) {
            homeWorld = filters.find(session.discordUserId())
                    .map(de.chojo.lolorito.entity.OfferFilterRow::worldId)
                    .orElse(0);
        }
        if (homeWorld <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "homeWorld required"));
            return;
        }
        int limit = QueryParams.intOr(ctx, "limit", DEFAULT_LIMIT, 1, 200);
        int minLevel = QueryParams.intOr(ctx, "minLevel", 0, 0, 100);
        var classFilter = parseClassFilter(ctx.queryParam("class"));
        // Buy-probe scope: home DC unless the caller explicitly widens to
        // the region (DC hops are cheap; region travel is substantial).
        boolean regionWide = "region".equalsIgnoreCase(ctx.queryParam("scope"));
        var language = prefs.languageFor(session.discordUserId());
        ctx.json(service.topCandidatesFor(
                homeWorld, limit, language, session.discordUserId(), classFilter, minLevel, regionWide));
    }

    private static Set<String> parseClassFilter(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
