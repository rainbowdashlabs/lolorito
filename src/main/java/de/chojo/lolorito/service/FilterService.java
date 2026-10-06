/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.OfferFilterRow;
import de.chojo.lolorito.entity.OfferFilterTarget;
import de.chojo.lolorito.repository.OfferFilters;
import de.chojo.universalis.worlds.World;

import static java.lang.Math.clamp;

/**
 * Read/update the current user's {@code offer_filter} row. The web layer
 * PUTs a {@link FilterPatch}; the Discord {@code /offers filter} handler
 * calls {@link #applyDiscordOptions} — both paths land in
 * {@link #update(long, FilterPatch)}.
 */
@Singleton
public class FilterService {
    private final OfferFilters repo;

    @Inject
    public FilterService(OfferFilters repo) {
        this.repo = repo;
    }

    private static <T> T orElse(T v, T fallback) {
        return v == null ? fallback : v;
    }

    private static String normalise(String target) {
        if (target == null) return "DATA_CENTER";
        var upper = target.toUpperCase();
        return upper.equals("REGION") || upper.equals("DATA_CENTER") ? upper : "DATA_CENTER";
    }

    /**
     * Current filter for {@code userId} — defaults if the row is absent.
     */
    public OfferFilterRow current(long userId) {
        return repo.find(userId).orElseGet(OfferFilterRow::defaults);
    }

    /**
     * Reset the caller's saved filter back to defaults. The row is
     * deleted rather than reset in place so a "current" read after
     * this call returns {@link OfferFilterRow#defaults()} — matching
     * what a fresh user would see.
     */
    public OfferFilterRow reset(long userId) {
        repo.delete(userId);
        return OfferFilterRow.defaults();
    }

    /**
     * Merge {@code patch} onto the persisted row and persist. Fields with
     * {@code null} in the patch keep their current value.
     */
    public OfferFilterRow update(long userId, FilterPatch patch) {
        var current = current(userId);
        var next = new OfferFilterRow(
                orElse(patch.worldId(), current.worldId()),
                clamp(orElse(patch.offerLimit(), current.offerLimit()), 1, 10_000),
                clamp(orElse(patch.unitPrice(), current.unitPrice()), 0, Integer.MAX_VALUE),
                clamp(orElse(patch.factor(), current.factor()), 0.0, 100.0),
                clamp(orElse(patch.refreshHours(), current.refreshHours()), 1, 168),
                clamp(orElse(patch.popularity(), current.popularity()), 0.0, 100.0),
                clamp(orElse(patch.marketVolume(), current.marketVolume()), 0.0, 100.0),
                clamp(orElse(patch.interest(), current.interest()), 0.0, 100.0),
                clamp(orElse(patch.sales(), current.sales()), 0, Integer.MAX_VALUE),
                clamp(orElse(patch.views(), current.views()), 0, Integer.MAX_VALUE),
                clamp(orElse(patch.profit(), current.profit()), 0, Integer.MAX_VALUE),
                clamp(orElse(patch.effectiveProfit(), current.effectiveProfit()), 0, Integer.MAX_VALUE),
                normalise(orElse(patch.target(), current.target())));
        repo.upsert(userId, next);
        return next;
    }

    /**
     * Discord-side entry point — build a partial patch from an
     * {@link DiscordOptions} bundle. Any {@code null} value is a "leave
     * alone" signal, matching how the SPA sends its PATCH.
     */
    public OfferFilterRow applyDiscordOptions(long userId, DiscordOptions options) {
        var target = options.target();
        return update(
                userId,
                new FilterPatch(
                        options.world() == null ? null : options.world().id(),
                        options.limit(),
                        options.unitPrice(),
                        options.factor(),
                        options.refreshHours(),
                        options.popularity(),
                        options.marketVolume(),
                        options.interest(),
                        options.sales(),
                        options.views(),
                        options.profit(),
                        options.effectiveProfit(),
                        target == null ? null : target.name()));
    }

    /**
     * Every field nullable — the PUT body is a partial patch.
     */
    public record FilterPatch(
            Integer worldId,
            Integer offerLimit,
            Integer unitPrice,
            Double factor,
            Integer refreshHours,
            Double popularity,
            Double marketVolume,
            Double interest,
            Integer sales,
            Integer views,
            Integer profit,
            Integer effectiveProfit,
            String target) {}

    /**
     * Nullable option bundle the Discord {@code /offers filter} handler builds
     * from slash-command options. {@code null} means "user didn't provide this
     * option — leave the field alone".
     */
    public record DiscordOptions(
            World world,
            Integer limit,
            Integer unitPrice,
            Double factor,
            Integer refreshHours,
            Double popularity,
            Double marketVolume,
            Double interest,
            Integer sales,
            Integer views,
            Integer profit,
            Integer effectiveProfit,
            OfferFilterTarget target) {}
}
