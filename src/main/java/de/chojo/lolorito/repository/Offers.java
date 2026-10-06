/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.OfferFilterTarget;
import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.PriceDistribution;
import de.chojo.lolorito.value.SaleRate;

import java.util.List;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Read side for the offers feed. Joins current cross-world listings with
 * the home-world market model in a single round trip; the service layer
 * scores and orders the result.
 */
@Singleton
public class Offers {
    @Inject
    public Offers(DataSource dataSource) {
        // sadu 2 uses the default QueryConfiguration
    }

    /**
     * All fresh cross-world listings for items that have a sufficient
     * home-world model. Search scope decides how wide we cast the net:
     * {@link OfferFilterTarget#DATA_CENTER} restricts to worlds in the
     * caller's home DC (the historical default);
     * {@link OfferFilterTarget#REGION} widens to every world in every
     * DC of the caller's region.
     */
    public List<Candidate> candidates(
            int homeWorldId, int dataCenterId, String regionName, OfferFilterTarget scope, int refreshHours, int cap) {
        // Scope is a static enum → SQL fragment is safe from injection.
        // Group by (world, item, hq, unit_price) so we get one candidate per
        // price cell instead of one per physical listing. Three separate
        // listings at the same price were previously bubbling up as three
        // identical rows in the retainer basket and burning solver budget
        // for no reason. sum(quantity) preserves the depth at that price.
        boolean isRegion = scope == OfferFilterTarget.REGION;
        String scopePredicate = isRegion ? "w.region_name = :scope_key" : "w.data_center::text = :scope_key";
        String scopeKey = isRegion ? (regionName == null ? "" : regionName) : String.valueOf(dataCenterId);
        // ORDER BY a cheap margin upper bound (median − buy price, per unit,
        // times depth) so a binding LIMIT keeps the *plausible* candidates.
        // Without it Postgres returns an arbitrary subset and the "top
        // offers" are the top of a random sample.
        //
        // depth_ahead: units already listed on the home world at or below
        // the model median — the queue a new listing joins. The value
        // engine adds it to the shelf-time estimate.
        return query("""
                SELECT l.world AS source_world_id,
                       l.item  AS item_id,
                       l.hq,
                       l.unit_price,
                       sum(l.quantity)::int AS quantity,
                       m.price_mu,
                       m.price_sigma,
                       m.weighted_n,
                       m.lambda_p95,
                       m.lambda_p100,
                       m.lambda_p105,
                       m.lambda_undercut,
                       m.ghost_fraction,
                       m.sample_count,
                       m.pooled,
                       m.fitted_at,
                       coalesce((SELECT sum(hl.quantity)::int
                                   FROM listings hl
                                  WHERE hl.world = :home
                                    AND hl.item = l.item
                                    AND hl.hq = l.hq
                                    AND hl.unit_price <= exp(m.price_mu)), 0) AS depth_ahead
                  FROM listings l
                  JOIN worlds w ON l.world = w.world
                  JOIN listings_updated lu ON l.world = lu.world AND l.item = lu.item
                  JOIN market_model m
                       ON m.world_id = :home
                      AND m.item_id  = l.item
                      AND m.hq       = l.hq
                      AND m.sufficient = TRUE
                 WHERE %s
                   AND l.world != :home
                   AND lu.updated >= now() - (:hours || ' hours')::INTERVAL
                 GROUP BY l.world, l.item, l.hq, l.unit_price,
                          m.price_mu, m.price_sigma, m.weighted_n,
                          m.lambda_p95, m.lambda_p100, m.lambda_p105,
                          m.lambda_undercut, m.ghost_fraction, m.sample_count,
                          m.pooled, m.fitted_at
                 ORDER BY (exp(m.price_mu) - l.unit_price) * sum(l.quantity) DESC
                 LIMIT :cap
                """.formatted(scopePredicate))
                .single(call().bind("home", homeWorldId)
                        .bind("scope_key", scopeKey)
                        .bind("hours", String.valueOf(refreshHours))
                        .bind("cap", cap))
                .map(row -> {
                    double weightedN = row.getObject("weighted_n") == null
                            ? row.getInt("sample_count")
                            : row.getDouble("weighted_n");
                    var price =
                            new PriceDistribution(row.getDouble("price_mu"), row.getDouble("price_sigma"), weightedN);
                    var rate = new SaleRate(
                            row.getDouble("lambda_p95"), row.getDouble("lambda_p100"), row.getDouble("lambda_p105"));
                    int itemId = row.getInt("item_id");
                    boolean hq = row.getBoolean("hq");
                    var model = new MarketModel(
                            itemId,
                            homeWorldId,
                            hq,
                            price,
                            rate,
                            row.getDouble("lambda_undercut"),
                            row.getDouble("ghost_fraction"),
                            row.getInt("sample_count"),
                            true,
                            row.getBoolean("pooled"),
                            row.get("fitted_at", INSTANT_TIMESTAMP));
                    return new Candidate(
                            row.getInt("source_world_id"),
                            itemId,
                            hq,
                            row.getInt("quantity"),
                            row.getInt("unit_price"),
                            row.getInt("depth_ahead"),
                            model);
                })
                .all();
    }

    /**
     * One raw candidate — a listing paired with the home-world model that
     * governs it and the home-queue depth its units would join.
     */
    public record Candidate(
            int sourceWorldId, int itemId, boolean hq, int quantity, int buyPrice, int depthAhead, MarketModel model) {}
}
