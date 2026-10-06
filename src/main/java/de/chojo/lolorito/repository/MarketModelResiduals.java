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
import java.util.Optional;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Residual store — one row per Universalis-observed sale, keyed by
 * {@code (item, world, hq)}, storing the log-ratio between the sale's
 * unit price and the market model's expected price at time of sale.
 *
 * <p>The fitter reads {@link #windowedSummary(int, int, boolean, int)} to
 * produce a Bayesian shrinkage prior; the calibration widget reads
 * {@link #globalSummary(int)} for the aggregate "how well did we do
 * last week" number.
 */
@Singleton
public class MarketModelResiduals {
    @Inject
    public MarketModelResiduals(DataSource dataSource) {
        // sadu 2 uses the default QueryConfiguration
    }

    /**
     * Insert one residual row.
     */
    public void insert(int itemId, int worldId, boolean hq, Instant observedAt, double logRatio) {
        query("""
                INSERT INTO market_model_residuals (item_id, world_id, hq, observed_at, log_ratio)
                VALUES (:item, :world, :hq, :at, :ratio)
                """)
                .single(call().bind("item", itemId)
                        .bind("world", worldId)
                        .bind("hq", hq)
                        .bind("at", observedAt, INSTANT_TIMESTAMP)
                        .bind("ratio", logRatio))
                .insert();
    }

    /**
     * Mean and sample-count for the last {@code windowDays} of residuals
     * for one key. Absent when the key has no observations.
     */
    public Optional<Summary> windowedSummary(int itemId, int worldId, boolean hq, int windowDays) {
        return query("""
                SELECT count(*)              AS n,
                       coalesce(avg(log_ratio), 0.0) AS mean,
                       coalesce(stddev_samp(log_ratio), 0.0) AS sigma
                  FROM market_model_residuals
                 WHERE item_id = :item AND world_id = :world AND hq = :hq
                   AND observed_at >= now() - (:days || ' days')::INTERVAL
                """)
                .single(call().bind("item", itemId)
                        .bind("world", worldId)
                        .bind("hq", hq)
                        .bind("days", String.valueOf(windowDays)))
                .map(row -> new Summary(row.getInt("n"), row.getDouble("mean"), row.getDouble("sigma")))
                .first()
                .filter(s -> s.count() > 0);
    }

    /**
     * Cross-key aggregate over the last {@code windowDays}. Used by the
     * calibration widget on the dashboard. The plain mean is dominated by
     * whatever handful of keys trade the most; {@code keyMedian} — the
     * median of per-key mean residuals — weights every key once and is
     * the honest "is the typical key calibrated" number.
     */
    public GlobalSummary globalSummary(int windowDays) {
        return query("""
                SELECT count(*)                               AS n,
                       coalesce(avg(log_ratio), 0.0)          AS mean,
                       coalesce(stddev_samp(log_ratio), 0.0)  AS sigma,
                       coalesce((SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY key_mean)
                                   FROM (SELECT avg(log_ratio) AS key_mean
                                           FROM market_model_residuals
                                          WHERE observed_at >= now() - (:days || ' days')::interval
                                          GROUP BY item_id, world_id, hq) keys), 0.0) AS key_median
                  FROM market_model_residuals
                 WHERE observed_at >= now() - (:days || ' days')::interval
                """)
                .single(call().bind("days", String.valueOf(windowDays)))
                .map(row -> new GlobalSummary(
                        row.getInt("n"), row.getDouble("mean"), row.getDouble("sigma"), row.getDouble("key_median")))
                .first()
                .orElseGet(() -> new GlobalSummary(0, 0.0, 0.0, 0.0));
    }

    /**
     * Aggregate roll-up. {@code keyMedian} is the median of per-key mean
     * log-ratios — volume-independent, one vote per key.
     */
    public record GlobalSummary(int count, double mean, double sigma, double keyMedian) {}

    /**
     * The worst-calibrated keys in the window — largest absolute mean log
     * ratio, gated on a minimum observation count so a single weird sale
     * can't top the list. This is the actionable drilldown behind the
     * one-number dashboard aggregate: which (item, world, hq) should a
     * human look at.
     */
    public List<KeySummary> worstKeys(int windowDays, int minCount, int limit) {
        return query("""
                SELECT item_id, world_id, hq,
                       count(*)::int                        AS n,
                       avg(log_ratio)                       AS mean,
                       coalesce(stddev_samp(log_ratio), 0.0) AS sigma
                  FROM market_model_residuals
                 WHERE observed_at >= now() - (:days || ' days')::INTERVAL
                 GROUP BY item_id, world_id, hq
                HAVING count(*) >= :min_count
                 ORDER BY abs(avg(log_ratio)) DESC
                 LIMIT :lim
                """)
                .single(call().bind("days", String.valueOf(Math.max(1, windowDays)))
                        .bind("min_count", Math.max(1, minCount))
                        .bind("lim", limit))
                .map(row -> new KeySummary(
                        row.getInt("item_id"),
                        row.getInt("world_id"),
                        row.getBoolean("hq"),
                        row.getInt("n"),
                        row.getDouble("mean"),
                        row.getDouble("sigma")))
                .all();
    }

    /** Per-key calibration roll-up for the drilldown table. */
    public record KeySummary(int itemId, int worldId, boolean hq, int count, double mean, double sigma) {}

    /**
     * Residual roll-up.
     *
     * @param count number of observations in the window
     * @param mean  mean of {@code log(actual / predicted)} — positive means
     *              actual prices sit above what the model predicts
     * @param sigma sample stddev of the log ratio; 0 when count &lt; 2
     */
    public record Summary(int count, double mean, double sigma) {}
}
