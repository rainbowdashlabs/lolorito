/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.PriceDistribution;
import de.chojo.lolorito.value.SaleRate;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * SQL access to {@code lolorito.market_model}. Business logic lives in the value/service layer.
 */
@Singleton
public class MarketModels {
    @Inject
    public MarketModels(DataSource dataSource) {
        // sadu 2 uses the default QueryConfiguration
    }

    private static MarketModel readRow(Row row) throws SQLException {
        // weighted_n is null for rows fitted before patch_31 — fall back
        // to the raw count, which is what readers historically used.
        double weightedN =
                row.getObject("weighted_n") == null ? row.getInt("sample_count") : row.getDouble("weighted_n");
        var price = new PriceDistribution(row.getDouble("price_mu"), row.getDouble("price_sigma"), weightedN);
        var rate =
                new SaleRate(row.getDouble("lambda_p95"), row.getDouble("lambda_p100"), row.getDouble("lambda_p105"));
        return new MarketModel(
                row.getInt("item_id"),
                row.getInt("world_id"),
                row.getBoolean("hq"),
                price,
                rate,
                row.getDouble("lambda_undercut"),
                row.getDouble("ghost_fraction"),
                row.getInt("sample_count"),
                row.getBoolean("sufficient"),
                row.getBoolean("pooled"),
                row.get("fitted_at", INSTANT_TIMESTAMP));
    }

    public void upsert(MarketModel m) {
        query("""
                INSERT INTO market_model (item_id, world_id, hq,
                                          price_mu, price_sigma, weighted_n,
                                          lambda_p95, lambda_p100, lambda_p105,
                                          lambda_undercut, ghost_fraction,
                                          sample_count, sufficient, pooled, fitted_at)
                VALUES (:item, :world, :hq,
                        :mu, :sigma, :weighted_n,
                        :p_low, :p_mid, :p_high,
                        :luc, :ghost,
                        :n, :ok, :pooled, :ts)
                ON CONFLICT (item_id, world_id, hq) DO UPDATE
                    SET price_mu        = excluded.price_mu,
                        price_sigma     = excluded.price_sigma,
                        weighted_n      = excluded.weighted_n,
                        lambda_p95      = excluded.lambda_p95,
                        lambda_p100     = excluded.lambda_p100,
                        lambda_p105     = excluded.lambda_p105,
                        lambda_undercut = excluded.lambda_undercut,
                        ghost_fraction  = excluded.ghost_fraction,
                        sample_count    = excluded.sample_count,
                        sufficient      = excluded.sufficient,
                        pooled          = excluded.pooled,
                        fitted_at       = excluded.fitted_at
                """)
                .single(call().bind("item", m.itemId())
                        .bind("world", m.worldId())
                        .bind("hq", m.hq())
                        .bind("mu", m.price().mu())
                        .bind("sigma", m.price().sigma())
                        .bind("weighted_n", m.price().weightedN())
                        .bind("p_low", m.saleRate().lambdaP95())
                        .bind("p_mid", m.saleRate().lambdaP100())
                        .bind("p_high", m.saleRate().lambdaP105())
                        .bind("luc", m.lambdaUndercut())
                        .bind("ghost", m.ghostFraction())
                        .bind("n", m.sampleCount())
                        .bind("ok", m.sufficient())
                        .bind("pooled", m.pooled())
                        .bind("ts", m.fittedAt(), INSTANT_TIMESTAMP))
                .insert();
    }

    /**
     * Batched lookup for one world + quality across many items — one round
     * trip instead of the per-component N+1 the desynth explorer used to
     * fire. Missing keys are simply absent from the map.
     */
    public java.util.Map<Integer, MarketModel> findAll(int worldId, java.util.Collection<Integer> itemIds, boolean hq) {
        if (itemIds.isEmpty()) return java.util.Map.of();
        String inList = itemIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        var out = new java.util.HashMap<Integer, MarketModel>();
        query("""
                SELECT * FROM market_model
                 WHERE world_id = :world AND hq = :hq AND item_id IN (%s)
                """.formatted(inList))
                .single(call().bind("world", worldId).bind("hq", hq))
                .map(MarketModels::readRow)
                .all()
                .forEach(m -> out.put(m.itemId(), m));
        return out;
    }

    public Optional<MarketModel> find(int worldId, int itemId, boolean hq) {
        return query("""
                SELECT * FROM market_model
                 WHERE item_id = :item AND world_id = :world AND hq = :hq
                """)
                .single(call().bind("item", itemId).bind("world", worldId).bind("hq", hq))
                .map(MarketModels::readRow)
                .first();
    }

    public List<KeyRef> refitCandidates(Instant cutoff, int rateWindowDays, int limit) {
        // Candidate keys: own-world sales, plus zero-sale keys that still
        // have a live listing while the rest of the DC does have sales
        // (those can only be priced by the DC-pooled fallback).
        //
        // Scheduling order is the load-bearing part. With hundreds of
        // thousands of keys and a bounded per-cycle batch, "busiest first"
        // re-fits the same hot keys every cycle and the tail — including
        // the worlds users actually trade on — never gets a model at all.
        // Tiers instead:
        //   0  never-fitted keys on a user's home world (offer_filter) —
        //      these directly gate what users see; drain them first
        //   1  never-fitted keys anywhere — backlog, drained fairly
        //   2  stale keys, oldest fit first — rotation, not king-of-the-hill
        // Sale count only breaks ties within a tier.
        return query("""
                WITH home_worlds AS (
                    SELECT DISTINCT world FROM offer_filter WHERE world > 0
                ),
                own AS (
                    SELECT s.world AS world_id, s.item AS item_id, s.hq,
                           count(*) AS sale_count, max(m.fitted_at) AS fitted_at
                      FROM sales s
                      LEFT JOIN market_model m
                           ON m.item_id = s.item AND m.world_id = s.world AND m.hq = s.hq
                     WHERE s.sold >= now() - (:days || ' days')::INTERVAL
                     GROUP BY s.world, s.item, s.hq
                    HAVING max(m.fitted_at) IS NULL OR max(m.fitted_at) < :cutoff
                ),
                pooled AS (
                    SELECT l.world AS world_id, l.item AS item_id, l.hq,
                           count(ds.*) AS sale_count, max(m.fitted_at) AS fitted_at
                      FROM listings l
                      JOIN worlds w ON l.world = w.world
                      JOIN sales ds ON ds.item = l.item AND ds.hq = l.hq
                      JOIN worlds dw ON ds.world = dw.world AND dw.data_center = w.data_center
                      LEFT JOIN market_model m
                           ON m.item_id = l.item AND m.world_id = l.world AND m.hq = l.hq
                     WHERE ds.sold >= now() - (:days || ' days')::INTERVAL
                       AND ds.world != l.world
                       AND NOT EXISTS (
                           SELECT 1 FROM sales os
                            WHERE os.world = l.world AND os.item = l.item AND os.hq = l.hq
                              AND os.sold >= now() - (:days || ' days')::INTERVAL)
                     GROUP BY l.world, l.item, l.hq
                    HAVING max(m.fitted_at) IS NULL OR max(m.fitted_at) < :cutoff
                ),
                unioned AS (
                    SELECT * FROM own
                    UNION ALL
                    SELECT * FROM pooled
                )
                SELECT world_id, item_id, hq
                  FROM unioned c
                 ORDER BY CASE
                              WHEN c.fitted_at IS NULL
                                   AND c.world_id IN (SELECT world FROM home_worlds) THEN 0
                              WHEN c.fitted_at IS NULL THEN 1
                              ELSE 2
                          END,
                          c.fitted_at ASC NULLS FIRST,
                          c.sale_count DESC
                 LIMIT :lim
                """)
                .single(call().bind("cutoff", cutoff, INSTANT_TIMESTAMP)
                        .bind("days", String.valueOf(rateWindowDays))
                        .bind("lim", limit))
                .map(row -> new KeyRef(row.getInt("world_id"), row.getInt("item_id"), row.getBoolean("hq")))
                .all();
    }

    /**
     * Flip {@code sufficient} off for models that haven't been refitted in
     * {@code windowDays}. {@code refitCandidates} refits any key with sales
     * inside the window every cycle, so a model this stale means its market
     * dried up — its last fit describes sales that no longer exist (the
     * sales table itself is cleaned at 60 days). Without this, dead keys
     * keep recommending flips off months-old numbers forever.
     *
     * @return rows demoted this pass
     */
    public int expireStale(int windowDays) {
        return query("""
                UPDATE market_model
                   SET sufficient = FALSE
                 WHERE sufficient
                   AND fitted_at < now() - (:days || ' days')::INTERVAL
                """)
                .single(call().bind("days", String.valueOf(Math.max(1, windowDays))))
                .update()
                .rows();
    }

    public record KeyRef(int worldId, int itemId, boolean hq) {}
}
