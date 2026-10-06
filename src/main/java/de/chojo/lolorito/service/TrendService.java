/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.repository.SalesTrends;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;

import java.util.Comparator;
import java.util.List;

/**
 * Business logic for {@code GET /api/v1/trends} — which items on the home
 * world are gaining or losing sales momentum, with a next-24h unit
 * forecast off the fitted regression line.
 *
 * <p>Ranking uses the raw slope (units/day per day): it answers "where is
 * demand moving in absolute terms", which is what a trader restocks
 * against. {@code relativeSlope} (slope ÷ mean) ships alongside so the UI
 * can flag "×2 overnight" spikes on quiet items without letting a
 * 1→3 units/day item outrank a 300→280 one.
 */
@Singleton
public class TrendService {

    /** Ignore keys with almost no volume — a 2-sale week fits a "trend" of pure noise. */
    private static final int MIN_UNITS_IN_WINDOW = 5;
    /** Below this fit quality the regression line explains little; the UI badges these rows. */
    private static final double WEAK_FIT_R2 = 0.3;

    private final SalesTrends repo;
    private final NameSupplier itemNames;
    private final ResponseCache<CacheKey, TrendBoard> cache;

    @Inject
    public TrendService(File config, SalesTrends repo, NameSupplier itemNames) {
        this.repo = repo;
        this.itemNames = itemNames;
        this.cache = new ResponseCache<>(
                config.value().responseCacheSeconds(), config.value().responseCacheMaxSize());
    }

    public TrendBoard board(int homeWorldId, int windowDays, int limit, Language language) {
        var key = new CacheKey(homeWorldId, windowDays, limit, language);
        return cache.get(key, k -> compute(k.homeWorldId(), k.windowDays(), k.limit(), k.language()));
    }

    /**
     * Trend for one key — the item page's strip. Empty when the item has
     * no sales in the window at all. Not cached separately: one key is a
     * single cheap regression and the page already polls slowly.
     */
    public java.util.Optional<TrendRow> forItem(
            int homeWorldId, int itemId, boolean hq, int windowDays, Language language) {
        return repo.fitOne(homeWorldId, itemId, hq, windowDays).map(t -> toRow(t, windowDays, language));
    }

    private TrendBoard compute(int homeWorldId, int windowDays, int limit, Language language) {
        var trends = repo.fit(homeWorldId, windowDays, MIN_UNITS_IN_WINDOW);

        var rows = trends.stream().map(t -> toRow(t, windowDays, language)).toList();
        var trending = rows.stream()
                .filter(r -> r.slope() > 0)
                .sorted(Comparator.comparingDouble(TrendRow::slope).reversed())
                .limit(limit)
                .toList();
        var losing = rows.stream()
                .filter(r -> r.slope() < 0)
                .sorted(Comparator.comparingDouble(TrendRow::slope))
                .limit(limit)
                .toList();
        return new TrendBoard(homeWorldId, windowDays, rows.size(), trending, losing);
    }

    private TrendRow toRow(SalesTrends.Trend t, int windowDays, Language language) {
        // The regression line evaluated at the NEXT day index is the
        // 24h forecast. Sales can't go negative — clamp; the slope still
        // carries the "dying" signal for ranking.
        double predicted = Math.max(0.0, t.intercept() + t.slope() * windowDays);
        double relativeSlope = t.avgUnits() > 0 ? t.slope() / t.avgUnits() : 0.0;
        return new TrendRow(
                t.itemId(),
                itemNameOf(t.itemId(), language),
                t.hq(),
                round2(t.slope()),
                round2(relativeSlope),
                round2(t.r2()),
                t.r2() < WEAK_FIT_R2,
                t.totalUnits(),
                round2(t.avgUnits()),
                t.lastDayUnits(),
                Math.round(predicted));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String itemNameOf(int itemId, Language language) {
        if (itemNames == null) return String.valueOf(itemId);
        var name = itemNames.fromId(itemId);
        if (name == null) return String.valueOf(itemId);
        String localised = name.get(language);
        if (localised != null && !localised.isBlank()) return localised;
        String english = name.get(Language.ENGLISH);
        return english == null ? String.valueOf(itemId) : english;
    }

    /**
     * @param slope          units/day gained (positive) or lost (negative) per day
     * @param relativeSlope  slope ÷ mean units/day — the "momentum" as a fraction
     * @param weakFit        true when r² is too low to trust the line
     * @param predictedNext24h regression forecast of units sold in the next
     *                         24 hours, clamped at 0
     */
    public record TrendRow(
            int itemId,
            String itemName,
            boolean hq,
            double slope,
            double relativeSlope,
            double r2,
            boolean weakFit,
            long totalUnits,
            double avgUnitsPerDay,
            long lastDayUnits,
            long predictedNext24h) {}

    public record TrendBoard(
            int homeWorldId, int windowDays, int fittedKeys, List<TrendRow> trending, List<TrendRow> losing) {}

    public record CacheKey(int homeWorldId, int windowDays, int limit, Language language) {}
}
