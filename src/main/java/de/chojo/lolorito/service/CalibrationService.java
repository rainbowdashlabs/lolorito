/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.repository.ListingEpisodes;
import de.chojo.lolorito.repository.MarketModelResiduals;
import de.chojo.lolorito.repository.MarketModels;
import de.chojo.lolorito.repository.PerfMetrics;
import de.chojo.lolorito.universalis.WorldNames;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Read-side wrapper around {@link MarketModelResiduals} for the dashboard.
 * Now exposes:
 * <ul>
 *   <li>{@link #recent()} / {@link #lastNDays(int)} — the point-in-time
 *       summary the widget already renders</li>
 *   <li>{@link #history(int)} — hourly snapshots stored by
 *       {@link CalibrationHistoryWorker}, for the echarts trend line</li>
 *   <li>{@link #capabilityStats()} — high-level "how healthy is Lolorito"
 *       stats — model coverage, alert delivery cadence, perf metrics</li>
 * </ul>
 */
@Singleton
public class CalibrationService {

    private static final int DEFAULT_WINDOW_DAYS = 14;
    private static final int DEFAULT_HISTORY_HOURS = 24 * 7; // one week

    private final MarketModelResiduals residuals;
    private final PerfMetrics perfMetrics;
    private final ListingEpisodes episodes;
    private final MarketModels models;
    private final de.chojo.universalis.provider.NameSupplier itemNames;

    @Inject
    public CalibrationService(
            MarketModelResiduals residuals,
            PerfMetrics perfMetrics,
            ListingEpisodes episodes,
            MarketModels models,
            de.chojo.universalis.provider.NameSupplier itemNames) {
        this.residuals = residuals;
        this.perfMetrics = perfMetrics;
        this.episodes = episodes;
        this.models = models;
        this.itemNames = itemNames;
    }

    /**
     * The worst-calibrated keys — the actionable drilldown behind the
     * dashboard's single aggregate number. Names resolved best-effort.
     */
    public List<KeyCalibration> worstKeys(int windowDays, int limit) {
        return residuals.worstKeys(Math.max(1, windowDays), MIN_DRILLDOWN_OBSERVATIONS, Math.max(1, limit)).stream()
                .map(k -> new KeyCalibration(
                        k.itemId(),
                        itemNameOf(k.itemId()),
                        k.worldId(),
                        WorldNames.nameOf(k.worldId()),
                        k.hq(),
                        k.count(),
                        k.mean(),
                        k.sigma(),
                        Math.expm1(k.mean())))
                .toList();
    }

    /** A key needs at least this many residuals before it can top the drilldown. */
    private static final int MIN_DRILLDOWN_OBSERVATIONS = 5;

    private String itemNameOf(int itemId) {
        if (itemNames == null) return String.valueOf(itemId);
        var name = itemNames.fromId(itemId);
        if (name == null) return String.valueOf(itemId);
        String english = name.get(de.chojo.universalis.entities.Language.ENGLISH);
        return english == null || english.isBlank() ? String.valueOf(itemId) : english;
    }

    /**
     * @param bias {@code exp(mean) − 1} — the fractional price misprediction,
     *             positive when realised prices run above the model
     */
    public record KeyCalibration(
            int itemId,
            String itemName,
            int worldId,
            String worldName,
            boolean hq,
            int count,
            double logRatioMean,
            double logRatioSigma,
            double bias) {}

    static String interpret(int count, double mean) {
        if (count == 0) return "No sales observed yet — feed the models with time in the wild.";
        double bias = Math.expm1(mean);
        if (Math.abs(bias) < 0.02) return "Predictions are within ±2 % of realised prices.";
        int percent = (int) Math.round(bias * 100);
        if (bias > 0) return "Realised prices are running about " + percent + " % above predictions.";
        return "Realised prices are running about " + Math.abs(percent) + " % below predictions.";
    }

    public Snapshot lastNDays(int windowDays) {
        int w = Math.max(1, windowDays);
        var s = residuals.globalSummary(w);
        return new Snapshot(w, s.count(), s.mean(), s.sigma(), s.keyMedian(), interpret(s.count(), s.mean()));
    }

    public Snapshot recent() {
        return lastNDays(DEFAULT_WINDOW_DAYS);
    }

    /** Rolling calibration snapshots, oldest → newest. */
    public List<HistoryPoint> history(int hours) {
        int h = Math.max(1, hours == 0 ? DEFAULT_HISTORY_HOURS : hours);
        return query("""
                SELECT captured_at, sample_count, log_ratio_mean, log_ratio_sigma
                  FROM calibration_snapshot
                 WHERE captured_at >= now() - (:h::text || ' HOURS')::INTERVAL
                 ORDER BY captured_at ASC
                """)
                .single(call().bind("h", h))
                .map(row -> new HistoryPoint(
                        row.get("captured_at", INSTANT_TIMESTAMP),
                        row.getInt("sample_count"),
                        row.getDouble("log_ratio_mean"),
                        row.getDouble("log_ratio_sigma")))
                .all();
    }

    /**
     * Shelf-time calibration — predicted vs realised time-to-sale over the
     * SOLD listing episodes the differ recorded. For each episode we ask
     * the current model "how long should {@code qty} units at this price
     * ratio have taken" and compare with how long the listing actually sat.
     *
     * <p>Caveats a reader should know: only episodes that ended in a sale
     * enter (fast sales are over-represented inside a short window), and
     * the model consulted is the <em>current</em> fit, not the one live
     * when the listing was posted. Good enough to spot systematic
     * optimism/pessimism, which is the point.
     */
    public ShelfSnapshot shelfTime(int windowDays, int maxEpisodes) {
        var sold = episodes.recentSold(Math.max(1, windowDays), Math.max(1, maxEpisodes));

        // Group by (world, hq) so model lookups batch per group instead of
        // one query per episode.
        Map<Long, List<ListingEpisodes.SoldEpisode>> byWorldHq = new HashMap<>();
        for (var e : sold) {
            byWorldHq
                    .computeIfAbsent(((long) e.world() << 1) | (e.hq() ? 1 : 0), k -> new ArrayList<>())
                    .add(e);
        }

        int n = 0;
        double sumLog = 0.0;
        double sumLogSq = 0.0;
        for (var entry : byWorldHq.entrySet()) {
            var group = entry.getValue();
            int world = (int) (entry.getKey() >> 1);
            boolean hq = (entry.getKey() & 1) == 1;
            var itemIds = group.stream()
                    .map(ListingEpisodes.SoldEpisode::item)
                    .distinct()
                    .toList();
            var groupModels = models.findAll(world, itemIds, hq);
            for (var e : group) {
                var model = groupModels.get(e.item());
                if (model == null || !model.sufficient()) continue;
                double median = model.price().medianPrice();
                if (median <= 0) continue;
                double lambda = model.saleRate().at(e.unitPrice() / median);
                if (lambda <= 0) continue;
                double predictedHours = Math.max(1, e.quantity()) / lambda;
                double realisedHours =
                        Duration.between(e.firstSeen(), e.endedAt()).toSeconds() / 3600.0;
                if (realisedHours <= 0 || predictedHours <= 0) continue;
                double logRatio = Math.log(realisedHours / predictedHours);
                n++;
                sumLog += logRatio;
                sumLogSq += logRatio * logRatio;
            }
        }
        double mean = n > 0 ? sumLog / n : 0.0;
        double sigma = n > 1 ? Math.sqrt(Math.max(0, sumLogSq / n - mean * mean)) : 0.0;
        return new ShelfSnapshot(windowDays, sold.size(), n, mean, sigma, interpretShelf(n, mean));
    }

    static String interpretShelf(int count, double mean) {
        if (count == 0) return "No sold listing episodes yet — the differ needs time in the wild.";
        double factor = Math.exp(mean);
        if (factor > 1.15) {
            return "Listings sit about " + String.format(java.util.Locale.ROOT, "%.1f", factor)
                    + "× longer than predicted — shelf-time estimates are optimistic.";
        }
        if (factor < 0.87) {
            return "Listings clear about " + String.format(java.util.Locale.ROOT, "%.1f", 1.0 / factor)
                    + "× faster than predicted — shelf-time estimates are pessimistic.";
        }
        return "Predicted shelf times are within ±15 % of realised.";
    }

    /**
     * @param soldEpisodes  SOLD episodes found in the window
     * @param scoredCount   how many could be scored against a sufficient model
     * @param logRatioMean  mean of {@code log(realised / predicted)} — positive
     *                      means listings sit longer than the model claims
     */
    public record ShelfSnapshot(
            int windowDays,
            int soldEpisodes,
            int scoredCount,
            double logRatioMean,
            double logRatioSigma,
            String interpretation) {}

    /**
     * Blow away every fitted market model plus every recorded residual.
     * Next refit cycle starts from scratch — priors are gone. Used by
     * the admin danger button when we've fed the fitter bad data (e.g.
     * during a schema migration) and want a clean baseline.
     *
     * @return counts of rows removed per table, for the response.
     */
    public ResetSummary resetAll() {
        int models = query("DELETE FROM market_model").single(call()).delete().rows();
        int residuals = query("DELETE FROM market_model_residuals")
                .single(call())
                .delete()
                .rows();
        int snapshots = query("DELETE FROM calibration_snapshot")
                .single(call())
                .delete()
                .rows();
        return new ResetSummary(models, residuals, snapshots);
    }

    /** Counts of rows removed by {@link #resetAll()}. */
    public record ResetSummary(int models, int residuals, int snapshots) {}

    /**
     * Dashboard capability panel — one big JSON blob with the highest-
     * signal stats about the running instance.
     */
    public CapabilityStats capabilityStats() {
        long dbSize = perfMetrics.databaseSizeBytes();
        int totalModels = query("SELECT count(*)::int c FROM market_model")
                .single(call())
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
        int sufficientModels = query("SELECT count(*)::int c FROM market_model WHERE sufficient = TRUE")
                .single(call())
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
        int uniqueItems = query("SELECT count(DISTINCT item_id)::int c FROM market_model")
                .single(call())
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
        int uniqueWorlds = query("SELECT count(DISTINCT world_id)::int c FROM market_model")
                .single(call())
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
        long totalListings = query("SELECT count(*)::bigint c FROM listings")
                .single(call())
                .map(row -> row.getLong("c"))
                .first()
                .orElse(0L);
        long totalSales = query("SELECT count(*)::bigint c FROM sales")
                .single(call())
                .map(row -> row.getLong("c"))
                .first()
                .orElse(0L);
        long enabledAlerts = query("SELECT count(*)::bigint c FROM alert_rule WHERE enabled = TRUE")
                .single(call())
                .map(row -> row.getLong("c"))
                .first()
                .orElse(0L);
        long alertsFired24h =
                query("""
                SELECT count(*)::bigint c FROM alert_rule
                 WHERE last_triggered_at >= now() - '24 HOURS'::INTERVAL
                """).single(call()).map(row -> row.getLong("c")).first().orElse(0L);
        Instant lastRefit = query("SELECT max(fitted_at) AS t FROM market_model")
                .single(call())
                .map(row -> row.getObject("t") == null ? null : row.get("t", INSTANT_TIMESTAMP))
                .first()
                .orElse(null);
        var modelRefit = perfMetrics.recent("model_refit_ms", 20);
        var viewRefresh = perfMetrics.recent("view_refresh_total_ms", 20);
        return new CapabilityStats(
                totalModels,
                sufficientModels,
                uniqueItems,
                uniqueWorlds,
                totalListings,
                totalSales,
                enabledAlerts,
                alertsFired24h,
                lastRefit,
                dbSize,
                toPerfSeries(modelRefit),
                toPerfSeries(viewRefresh));
    }

    private static List<PerfPoint> toPerfSeries(List<PerfMetrics.Row> rows) {
        return rows.stream()
                .map(r -> new PerfPoint(r.capturedAt(), r.valueMs(), r.detail()))
                .toList();
    }

    /**
     * @param keyMedianLogRatio median of per-key mean residuals — one vote
     *                          per key, immune to volume dominance
     */
    public record Snapshot(
            int windowDays,
            int sampleCount,
            double logRatioMean,
            double logRatioSigma,
            double keyMedianLogRatio,
            String interpretation) {}

    public record HistoryPoint(Instant capturedAt, int sampleCount, double logRatioMean, double logRatioSigma) {}

    public record CapabilityStats(
            int totalModels,
            int sufficientModels,
            int uniqueItems,
            int uniqueWorlds,
            long totalListings,
            long totalSales,
            long enabledAlerts,
            long alertsFired24h,
            Instant lastRefitAt,
            long databaseBytes,
            List<PerfPoint> modelRefitMs,
            List<PerfPoint> viewRefreshMs) {}

    public record PerfPoint(Instant capturedAt, long valueMs, String detail) {}
}
