/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.service.FilterService;
import de.chojo.lolorito.service.OffersService;
import de.chojo.lolorito.service.UserPreferencesService;
import de.chojo.lolorito.value.OfferBounds;
import de.chojo.lolorito.value.UserPrefs;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Map;

/**
 * {@code GET /api/v1/offers?home_world=&limit=&refresh_hours=&attention=} —
 * scored feed sorted by attention-adjusted gil-per-hour. When {@code
 * home_world} and {@code refresh_hours} are omitted, they fall back to the
 * user's saved filter. All logic sits in {@link OffersService} —
 * {@code FilterService} supplies the persisted prefs.
 */
public class OffersRoutes implements Routes {
    private final File config;
    private final OffersService offers;
    private final FilterService filters;
    private final UserPreferencesService prefs;

    @Inject
    public OffersRoutes(File config, OffersService offers, FilterService filters, UserPreferencesService prefs) {
        this.config = config;
        this.offers = offers;
        this.filters = filters;
        this.prefs = prefs;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/offers", this::listOffers);
    }

    private void listOffers(Context ctx) {
        var session = SessionResolver.sessionOf(ctx);
        var saved = filters.current(session.discordUserId());

        Integer homeWorldId = QueryParams.optInt(ctx, "home_world");
        if (homeWorldId == null) homeWorldId = saved.worldId();
        if (homeWorldId <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("error", "home_world not set — provide ?home_world= or save one via /me/filter"));
            return;
        }
        World world = Worlds.worldById(homeWorldId);
        if (world == null || world.dataCenter() == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "unknown home_world"));
            return;
        }

        int limit = QueryParams.intOr(ctx, "limit", 50, 1, 500);
        int refreshHours = QueryParams.intOr(ctx, "refresh_hours", saved.refreshHours(), 1, 168);
        // ?attention= overrides; otherwise the attention fraction the user
        // saved with their planner defaults, so every surface ranks with
        // the same attention model.
        double attentionFraction =
                QueryParams.doubleOr(ctx, "attention", prefs.attentionFractionFor(session.discordUserId(), 0.25));
        var scope = saved.targetEnum();
        String regionName = world.dataCenter().region().name();

        var userPrefs = new UserPrefs(config.value().mbTaxFor(world.dataCenter().id()), attentionFraction, 30.0);
        var language = this.prefs.languageFor(session.discordUserId());
        var rows = offers.topOffers(
                world.id(),
                world.dataCenter().id(),
                regionName,
                scope,
                refreshHours,
                userPrefs,
                new OfferBounds(saved.budget(), saved.inventorySlots()),
                limit,
                language);

        ctx.json(Map.of(
                "homeWorld", world.name(),
                "dataCenter", world.dataCenter().name(),
                "region", regionName,
                "scope", scope.name(),
                "count", rows.size(),
                "offers", rows));
    }
}
