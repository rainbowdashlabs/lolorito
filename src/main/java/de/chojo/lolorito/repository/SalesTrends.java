/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Per-(item, hq) sales-trend regression on one world. Buckets the sales
 * log into zero-filled daily units and runs an ordinary least-squares fit
 * in Postgres ({@code regr_slope} / {@code regr_intercept} / {@code
 * regr_r2}) — the zero-fill is load-bearing: skipping quiet days would
 * bias every slope upward, because absent buckets are real zeroes, not
 * missing data.
 */
@Singleton
public class SalesTrends {
    @Inject
    public SalesTrends(DataSource dataSource) {
        // sadu 2 uses the default QueryConfiguration
    }

    /**
     * One fitted trend. Day indices run 0 (oldest) … windowDays−1 (the
     * bucket ending now), so a positive {@code slope} means sales are
     * accelerating.
     *
     * @param slope      units/day change per day (OLS slope)
     * @param intercept  units/day at day index 0
     * @param r2         goodness of fit, 0..1 — low values mean the trend
     *                   line explains little; treat the slope as noise
     * @param totalUnits units sold across the whole window
     * @param avgUnits   mean units/day across the window
     * @param lastDayUnits units sold in the most recent 24h bucket
     */
    public record Trend(
            int itemId,
            boolean hq,
            double slope,
            double intercept,
            double r2,
            long totalUnits,
            double avgUnits,
            long lastDayUnits) {}

    /**
     * Fit one (item, hq) key — the item-detail page's trend strip. Same
     * zero-filled regression as {@link #fit}, no volume gate: even a
     * single sale in the window produces an honest (weak-fit) line.
     */
    public java.util.Optional<Trend> fitOne(int worldId, int itemId, boolean hq, int windowDays) {
        return fit(worldId, windowDays, 1, itemId, hq).stream().findFirst();
    }

    /**
     * Fit every (item, hq) on {@code worldId} with at least
     * {@code minUnits} units sold inside the {@code windowDays} window.
     * The service layer ranks, predicts, and slices.
     */
    public List<Trend> fit(int worldId, int windowDays, int minUnits) {
        return fit(worldId, windowDays, minUnits, null, null);
    }

    private List<Trend> fit(int worldId, int windowDays, int minUnits, Integer itemId, Boolean hq) {
        String days = String.valueOf(Math.max(2, windowDays));
        return query("""
                WITH raw AS (
                    SELECT item, hq,
                           (:days::int - 1 - floor(extract(EPOCH FROM (now() - sold)) / 86400))::int AS d,
                           sum(quantity) AS units
                      FROM sales
                     WHERE world = :world
                       AND sold >= now() - (:days || ' days')::INTERVAL
                       AND (:item::int IS NULL OR item = :item::int)
                       AND (:hq::boolean IS NULL OR hq = :hq::boolean)
                     GROUP BY item, hq, 3
                ),
                keys AS (
                    SELECT item, hq
                      FROM raw
                     GROUP BY item, hq
                    HAVING sum(units) >= :min_units
                ),
                grid AS (
                    SELECT k.item, k.hq, g.d, coalesce(r.units, 0) AS units
                      FROM keys k
                     CROSS JOIN generate_series(0, :days::int - 1) AS g(d)
                      LEFT JOIN raw r ON r.item = k.item AND r.hq = k.hq AND r.d = g.d
                )
                SELECT item, hq,
                       regr_slope(units, d)          AS slope,
                       regr_intercept(units, d)      AS intercept,
                       coalesce(regr_r2(units, d), 0) AS r2,
                       sum(units)::bigint            AS total_units,
                       avg(units)                    AS avg_units,
                       sum(units) FILTER (WHERE d = :days::int - 1)::bigint AS last_day_units
                  FROM grid
                 GROUP BY item, hq
                HAVING regr_slope(units, d) IS NOT NULL
                """)
                .single(call().bind("world", worldId)
                        .bind("days", days)
                        .bind("min_units", Math.max(1, minUnits))
                        .bind("item", itemId)
                        .bind("hq", hq))
                .map(row -> new Trend(
                        row.getInt("item"),
                        row.getBoolean("hq"),
                        row.getDouble("slope"),
                        row.getDouble("intercept"),
                        row.getDouble("r2"),
                        row.getLong("total_units"),
                        row.getDouble("avg_units"),
                        row.getLong("last_day_units")))
                .all();
    }

    /** Units and gil sold on one world inside one clock hour starting at {@code hourStart}. */
    public record HourBucket(Instant hourStart, long units, long gil) {}

    /**
     * Sales on {@code worldId} over the last {@code hours} clock hours, one
     * bucket per hour (the current, partial hour last). Hours without sales
     * are present as zero buckets.
     */
    public List<HourBucket> hourly(int worldId, int hours) {
        return query("""
                WITH g AS (
                    SELECT generate_series(
                               date_trunc('hour', now()) - ((:hours::int - 1) || ' hours')::INTERVAL,
                               date_trunc('hour', now()),
                               INTERVAL '1 hour') AS h
                )
                SELECT g.h                              AS hour_start,
                       coalesce(sum(s.quantity), 0)::bigint AS units,
                       coalesce(sum(s.total), 0)::bigint    AS gil
                  FROM g
                  LEFT JOIN sales s
                    ON s.world = :world
                   AND s.sold >= g.h
                   AND s.sold < g.h + INTERVAL '1 hour'
                 GROUP BY g.h
                 ORDER BY g.h
                """)
                .single(call().bind("world", worldId).bind("hours", Math.max(1, hours)))
                .map(row -> new HourBucket(
                        row.get("hour_start", INSTANT_TIMESTAMP), row.getLong("units"), row.getLong("gil")))
                .all();
    }
}
