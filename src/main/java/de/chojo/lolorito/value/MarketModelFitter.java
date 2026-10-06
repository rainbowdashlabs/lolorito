/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.config.file.elements.Value;
import de.chojo.lolorito.repository.ListingEpisodes;
import de.chojo.lolorito.repository.MarketModelResiduals;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Loads recent sales for a (world, item, hq) key from the database and
 * produces a fitted {@link MarketModel}. Keeps DB access local to one class
 * so the pure {@code value.*} records stay unit-testable.
 *
 * <p>The fitter glues {@link PriceDistribution#fit} and {@link SaleRate#fit}
 * together, then decorates the model with the measured adversary signals
 * (undercuts/hour + ghost share from the listing-episode log) and, when the
 * key alone is too thin, falls back to <b>DC-pooled</b> sales: the same
 * (item, hq) across every world of the key's data center, with the sale
 * rate scaled down by the world count. Pooled rows are flagged so the UI
 * can badge them as lower-confidence — but a pooled estimate beats
 * refusing to value the long tail at all, and the long tail is where the
 * margin lives.
 *
 * <p>When a {@link MarketModelResiduals} store is supplied, the fit also
 * consumes a windowed residual mean and applies it as an additive Bayesian
 * shrinkage prior on {@code mu}. Residuals are measured against the
 * <em>median</em> (zero-mean under a calibrated model), so for a pooled fit
 * this doubles as the world-level offset from the DC-level price.
 * Shrinkage weight is {@code n / (n + priorK)} so sparse keys barely move.
 */
@Singleton
public class MarketModelFitter {

    private final Value config;
    private final MarketModelResiduals residuals;
    private final ListingEpisodes episodes;

    @Inject
    public MarketModelFitter(File file, MarketModelResiduals residuals, ListingEpisodes episodes) {
        this.config = file.value();
        this.residuals = residuals;
        this.episodes = episodes;
    }

    static double shrinkage(int count, int priorK) {
        if (count <= 0) return 0.0;
        int k = Math.max(0, priorK);
        return (double) count / (count + k);
    }

    /**
     * Fit a single key. Returns empty if neither the key nor its DC pool
     * has at least two sales (nothing to persist); returns an
     * insufficient-flagged model when there is some but not enough
     * weighted evidence.
     */
    public Optional<MarketModel> fit(int worldId, int itemId, boolean hq, Instant now) {
        List<SaleObservation> sales = loadRecentSales(worldId, itemId, hq, now);
        // A single sale carries no distributional information — one point
        // gives you σ = 0 and a mean equal to that point.
        Optional<PriceDistribution> price =
                sales.size() < 2 ? Optional.empty() : PriceDistribution.fit(sales, config.priceHalfLifeDays(), now);
        boolean keySufficient = price.isPresent() && price.get().weightedN() >= config.minSamplesPerKey();

        List<SaleObservation> fitSales = sales;
        boolean pooled = false;
        double rateScale = 1.0;

        if (!keySufficient) {
            var pool = poolFit(worldId, itemId, hq, now);
            if (pool != null) {
                price = Optional.of(pool.price());
                fitSales = pool.sales();
                pooled = true;
                rateScale = 1.0 / pool.worldCount();
            }
        }
        if (price.isEmpty()) return Optional.empty();

        PriceDistribution shrunk = applyResidualPrior(worldId, itemId, hq, price.get());

        SaleRate rate = SaleRate.fit(
                        fitSales, shrunk.medianPrice(), config.rateWindowDays(), config.priceHalfLifeDays(), now)
                .scaled(rateScale);

        boolean sufficient = shrunk.weightedN() >= config.minSamplesPerKey();

        return Optional.of(new MarketModel(
                itemId,
                worldId,
                hq,
                shrunk,
                rate,
                undercutsPerHour(worldId, itemId, hq),
                ghostFraction(worldId, itemId, hq),
                fitSales.size(),
                sufficient,
                pooled,
                now));
    }

    /** Pooled DC fit — non-null only when the pool is itself sufficient. */
    private PoolFit poolFit(int worldId, int itemId, boolean hq, Instant now) {
        int worldCount = worldsInDataCenter(worldId);
        if (worldCount <= 1) return null; // nothing to pool with
        List<SaleObservation> poolSales = loadRecentSalesDc(worldId, itemId, hq, now);
        if (poolSales.size() < 2) return null;
        var poolPrice = PriceDistribution.fit(poolSales, config.priceHalfLifeDays(), now)
                .orElse(null);
        if (poolPrice == null || poolPrice.weightedN() < config.minSamplesPerKey()) return null;
        return new PoolFit(poolPrice, poolSales, worldCount);
    }

    private record PoolFit(PriceDistribution price, List<SaleObservation> sales, int worldCount) {}

    private static int worldsInDataCenter(int worldId) {
        World world = Worlds.worldById(worldId);
        if (world == null || world.dataCenter() == null) return 1;
        return Math.max(1, world.dataCenter().worlds().size());
    }

    private double undercutsPerHour(int worldId, int itemId, boolean hq) {
        if (episodes == null) return 0.0;
        return episodes.undercutsPerHour(worldId, itemId, hq, config.rateWindowDays());
    }

    private double ghostFraction(int worldId, int itemId, boolean hq) {
        if (episodes == null) return 0.0;
        return episodes.ghostFraction(worldId, itemId, hq, config.ghostListingWindowDays());
    }

    /**
     * Shift the fitted {@code mu} by the shrinkage-weighted mean of recent
     * residuals for this key. No-op when residuals are unavailable, the
     * key has no observations, or shrinkage evaluates to 0.
     */
    private PriceDistribution applyResidualPrior(int worldId, int itemId, boolean hq, PriceDistribution fit) {
        if (residuals == null) return fit;
        var summary = residuals
                .windowedSummary(itemId, worldId, hq, config.residualWindowDays())
                .orElse(null);
        if (summary == null) return fit;
        double shrink = shrinkage(summary.count(), config.residualShrinkagePriorK());
        if (shrink <= 0.0) return fit;
        double adjustedMu = fit.mu() + shrink * summary.mean();
        return new PriceDistribution(adjustedMu, fit.sigma(), fit.weightedN());
    }

    private List<SaleObservation> loadRecentSales(int worldId, int itemId, boolean hq, Instant now) {
        var cutoff = now.minusSeconds((long) config.rateWindowDays() * 24 * 3600);
        return query("""
                SELECT unit_price, quantity, sold
                  FROM sales
                 WHERE world = :world AND item = :item AND hq = :hq
                   AND sold >= :cutoff
                """)
                .single(call().bind("world", worldId)
                        .bind("item", itemId)
                        .bind("hq", hq)
                        .bind("cutoff", cutoff, INSTANT_TIMESTAMP))
                .map(row -> new SaleObservation(
                        row.getInt("unit_price"), row.getInt("quantity"), row.get("sold", INSTANT_TIMESTAMP)))
                .all();
    }

    private List<SaleObservation> loadRecentSalesDc(int worldId, int itemId, boolean hq, Instant now) {
        var cutoff = now.minusSeconds((long) config.rateWindowDays() * 24 * 3600);
        return query("""
                SELECT s.unit_price, s.quantity, s.sold
                  FROM sales s
                  JOIN worlds w ON s.world = w.world
                 WHERE w.data_center = (SELECT data_center FROM worlds WHERE world = :world)
                   AND s.item = :item AND s.hq = :hq
                   AND s.sold >= :cutoff
                """)
                .single(call().bind("world", worldId)
                        .bind("item", itemId)
                        .bind("hq", hq)
                        .bind("cutoff", cutoff, INSTANT_TIMESTAMP))
                .map(row -> new SaleObservation(
                        row.getInt("unit_price"), row.getInt("quantity"), row.get("sold", INSTANT_TIMESTAMP)))
                .all();
    }
}
