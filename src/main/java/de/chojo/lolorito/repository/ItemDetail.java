/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Singleton;
import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.PriceDistribution;
import de.chojo.lolorito.value.SaleRate;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Read side for {@code GET /api/v1/items/:id}. Pulls per-world listings,
 * a home-world market model, and (for the craft path) cheapest buy prices
 * for a set of items on a data center.
 */
@Singleton
public class ItemDetail {
    private static MarketModel readModel(Row row, int homeWorldId, int itemId, boolean hq) throws SQLException {
        double weightedN =
                row.getObject("weighted_n") == null ? row.getInt("sample_count") : row.getDouble("weighted_n");
        var price = new PriceDistribution(row.getDouble("price_mu"), row.getDouble("price_sigma"), weightedN);
        var rate =
                new SaleRate(row.getDouble("lambda_p95"), row.getDouble("lambda_p100"), row.getDouble("lambda_p105"));
        return new MarketModel(
                itemId,
                homeWorldId,
                hq,
                price,
                rate,
                row.getDouble("lambda_undercut"),
                row.getDouble("ghost_fraction"),
                row.getInt("sample_count"),
                row.getBoolean("sufficient"),
                row.getBoolean("pooled"),
                row.get("fitted_at", INSTANT_TIMESTAMP));
    }

    public Optional<MarketModel> homeModel(int homeWorldId, int itemId, boolean hq) {
        return query("""
                SELECT * FROM market_model
                 WHERE world_id = :world AND item_id = :item AND hq = :hq
                """)
                .single(call().bind("world", homeWorldId).bind("item", itemId).bind("hq", hq))
                .map(row -> readModel(row, homeWorldId, itemId, hq))
                .first();
    }

    /**
     * Batched cheapest-price lookup for a set of item ids on either a
     * single world or a whole data center. Used by {@link
     * de.chojo.lolorito.service.AlertScanner} so it doesn't fire one
     * SQL round-trip per enabled rule. Freshness-gated: a world whose
     * snapshot for an item is older than {@code maxAgeHours} doesn't
     * contribute — a listing bought out days ago must not fire an alert.
     *
     * @param itemIds       ids to price (empty → empty result)
     * @param worldId       set for world-scoped alerts, null otherwise
     * @param dataCenterId  set for DC-scoped alerts, null otherwise
     * @param hq            {@code null} for either-quality, otherwise a filter
     * @param maxAgeHours   snapshot staleness horizon
     */
    public Map<Integer, Integer> cheapestByKeys(
            List<Integer> itemIds, Integer worldId, Integer dataCenterId, Boolean hq, int maxAgeHours) {
        if (itemIds.isEmpty()) return Map.of();
        String scopeClause;
        if (worldId != null) {
            scopeClause = "l.world = :scope";
        } else if (dataCenterId != null) {
            scopeClause = "w.data_center = :scope";
        } else {
            return Map.of();
        }
        int scopeValue = worldId != null ? worldId : dataCenterId;
        // The id list is caller-supplied but always Integer, so inlining the
        // values in the SQL is safe from injection and avoids sadu's
        // array-binding limitations.
        String inList = itemIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String sql = """
                SELECT l.item AS item_id, min(l.unit_price) AS cheapest
                  FROM listings l
                  JOIN worlds w ON l.world = w.world
                  JOIN listings_updated lu ON l.world = lu.world AND l.item = lu.item
                 WHERE l.item IN (%s)
                   AND (:hq IS NULL OR l.hq = :hq)
                   AND %s
                   AND lu.updated >= now() - (:hours || ' hours')::INTERVAL
                 GROUP BY l.item
                """.formatted(inList, scopeClause);
        Map<Integer, Integer> out = new HashMap<>();
        query(sql)
                .single(call().bind("hq", hq).bind("scope", scopeValue).bind("hours", String.valueOf(maxAgeHours)))
                .map(row -> {
                    Integer price = row.getObject("cheapest") == null ? null : row.getInt("cheapest");
                    if (price != null) out.put(row.getInt("item_id"), price);
                    return null;
                })
                .all();
        return out;
    }

    /**
     * Number of current listings per item on a world or a whole data
     * center, counted only where the board snapshot is younger than
     * {@code maxAgeHours}. Items with a fresh snapshot and no listings map
     * to 0; items without a fresh snapshot are absent.
     */
    public Map<Integer, Integer> listingCountByKeys(
            List<Integer> itemIds, Integer worldId, Integer dataCenterId, Boolean hq, int maxAgeHours) {
        if (itemIds.isEmpty() || (worldId == null && dataCenterId == null)) return Map.of();
        String scopeClause = worldId != null ? "lu.world = :scope" : "w.data_center = :scope";
        int scopeValue = worldId != null ? worldId : dataCenterId;
        String inList = itemIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String sql = """
                SELECT lu.item AS item_id, count(l.item) AS listings
                  FROM listings_updated lu
                  JOIN worlds w ON lu.world = w.world
                  LEFT JOIN listings l
                    ON l.world = lu.world AND l.item = lu.item AND (:hq IS NULL OR l.hq = :hq)
                 WHERE lu.item IN (%s)
                   AND %s
                   AND lu.updated >= now() - (:hours || ' hours')::INTERVAL
                 GROUP BY lu.item
                """.formatted(inList, scopeClause);
        Map<Integer, Integer> out = new HashMap<>();
        query(sql)
                .single(call().bind("hq", hq).bind("scope", scopeValue).bind("hours", String.valueOf(maxAgeHours)))
                .map(row -> {
                    out.put(row.getInt("item_id"), row.getInt("listings"));
                    return null;
                })
                .all();
        return out;
    }

    /**
     * Cheapest current listing for the given key on either a world or a
     * whole data center. Exactly one of {@code worldId} / {@code dataCenterId}
     * must be non-null. {@code hq} is treated as a filter — {@code null} means
     * "either quality". Returns empty if no listings match.
     */
    public Optional<Integer> cheapestPrice(int itemId, Integer worldId, Integer dataCenterId, Boolean hq) {
        String scopeClause;
        if (worldId != null) {
            scopeClause = "l.world = :scope";
        } else if (dataCenterId != null) {
            scopeClause = "w.data_center = :scope";
        } else {
            return Optional.empty();
        }
        int scopeValue = worldId != null ? worldId : dataCenterId;
        String sql = """
                SELECT min(l.unit_price) AS cheapest
                  FROM listings l
                  JOIN worlds w ON l.world = w.world
                 WHERE l.item = :item
                   AND (:hq IS NULL OR l.hq = :hq)
                   AND %s
                """.formatted(scopeClause);
        return query(sql)
                .single(call().bind("item", itemId).bind("hq", hq).bind("scope", scopeValue))
                .map(row -> row.getObject("cheapest") == null ? null : row.getInt("cheapest"))
                .first()
                .filter(java.util.Objects::nonNull);
    }

    /**
     * Cheapest cross-world unit prices on the data center for the given
     * item ids — one round trip, freshness-gated. Worlds whose snapshot for
     * an item is older than {@code maxAgeHours} don't contribute: a listing
     * bought out days ago must not anchor a craft or desynth valuation.
     * Missing items are absent from the returned map (the caller treats
     * absent as "no known price").
     */
    public Map<Integer, Integer> cheapestByItem(int dataCenterId, List<Integer> itemIds, boolean hq, int maxAgeHours) {
        if (itemIds.isEmpty()) return Map.of();
        String inList = itemIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        Map<Integer, Integer> out = new HashMap<>();
        query("""
                SELECT l.item AS item_id, min(l.unit_price) AS cheapest
                  FROM listings l
                  JOIN worlds w ON l.world = w.world
                  JOIN listings_updated lu ON l.world = lu.world AND l.item = lu.item
                 WHERE l.item IN (%s)
                   AND l.hq = :hq
                   AND w.data_center = :dc
                   AND lu.updated >= now() - (:hours || ' hours')::INTERVAL
                 GROUP BY l.item
                """.formatted(inList))
                .single(call().bind("hq", hq).bind("dc", dataCenterId).bind("hours", String.valueOf(maxAgeHours)))
                .map(row -> {
                    Integer price = row.getObject("cheapest") == null ? null : row.getInt("cheapest");
                    if (price != null) out.put(row.getInt("item_id"), price);
                    return null;
                })
                .all();
        return out;
    }

    /** One price step of the cheap side of an item's book, with the world holding the listing. */
    public record PriceLevel(int unitPrice, int quantity, int worldId) {}

    /**
     * The cheap side of the book — up to {@code levelsPerItem} price
     * steps per item, ascending, freshness-gated. Exactly one of
     * {@code dataCenterId} / {@code regionName} must be set. The caller
     * walks cumulative depth to price a real required quantity instead
     * of pretending the whole need fills at the single min-price row
     * (which may hold one unit).
     */
    public Map<Integer, List<PriceLevel>> cheapBook(
            Integer dataCenterId,
            String regionName,
            List<Integer> itemIds,
            boolean hq,
            int maxAgeHours,
            int levelsPerItem) {
        if (itemIds.isEmpty()) return Map.of();
        String scopeClause;
        if (dataCenterId != null) {
            scopeClause = "w.data_center = :scope::int";
        } else if (regionName != null) {
            scopeClause = "w.region_name = :scope";
        } else {
            return Map.of();
        }
        String scopeValue = dataCenterId != null ? String.valueOf(dataCenterId) : regionName;
        String inList = itemIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        Map<Integer, List<PriceLevel>> out = new HashMap<>();
        query("""
                SELECT item_id, unit_price, quantity, world_id
                  FROM (SELECT l.item AS item_id, l.unit_price, l.quantity, l.world AS world_id,
                               row_number() OVER (PARTITION BY l.item ORDER BY l.unit_price) AS rn
                          FROM listings l
                          JOIN worlds w ON l.world = w.world
                          JOIN listings_updated lu ON l.world = lu.world AND l.item = lu.item
                         WHERE l.item IN (%s)
                           AND l.hq = :hq
                           AND %s
                           AND lu.updated >= now() - (:hours || ' hours')::INTERVAL) x
                 WHERE rn <= :levels
                 ORDER BY item_id, unit_price
                """.formatted(inList, scopeClause))
                .single(call().bind("hq", hq)
                        .bind("scope", scopeValue)
                        .bind("hours", String.valueOf(maxAgeHours))
                        .bind("levels", levelsPerItem))
                .map(row -> {
                    out.computeIfAbsent(row.getInt("item_id"), k -> new java.util.ArrayList<>())
                            .add(new PriceLevel(
                                    row.getInt("unit_price"), row.getInt("quantity"), row.getInt("world_id")));
                    return null;
                })
                .all();
        return out;
    }

    /**
     * Region-wide cheapest listing per item id, freshness-gated the same
     * way. Feeds the desynth explorer's batched buy-price probe.
     */
    public Map<Integer, Integer> cheapestByItemRegion(
            String regionName, List<Integer> itemIds, boolean hq, int maxAgeHours) {
        if (itemIds.isEmpty()) return Map.of();
        String inList = itemIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        Map<Integer, Integer> out = new HashMap<>();
        query("""
                SELECT l.item AS item_id, min(l.unit_price) AS cheapest
                  FROM listings l
                  JOIN worlds w ON l.world = w.world
                  JOIN listings_updated lu ON l.world = lu.world AND l.item = lu.item
                 WHERE l.item IN (%s)
                   AND l.hq = :hq
                   AND w.region_name = :region
                   AND lu.updated >= now() - (:hours || ' hours')::INTERVAL
                 GROUP BY l.item
                """.formatted(inList))
                .single(call().bind("hq", hq).bind("region", regionName).bind("hours", String.valueOf(maxAgeHours)))
                .map(row -> {
                    Integer price = row.getObject("cheapest") == null ? null : row.getInt("cheapest");
                    if (price != null) out.put(row.getInt("item_id"), price);
                    return null;
                })
                .all();
        return out;
    }

    /**
     * DC-scoped listings for an item, ordered by unit price. Kept
     * separate from {@link #listingsForRegion} so callers who genuinely
     * only want the home DC (the crafting sourcing lookup) don't pull
     * unnecessary rows.
     */
    public List<ListingRow> listings(int itemId, int dataCenterId, boolean hq, int cap) {
        return query("""
                SELECT l.world, l.unit_price, l.quantity, l.hq, l.review_time
                  FROM listings l
                  JOIN worlds w ON l.world = w.world
                 WHERE l.item = :item
                   AND l.hq = :hq
                   AND w.data_center = :dc
                 ORDER BY l.unit_price ASC
                 LIMIT :cap
                """)
                .single(call().bind("item", itemId)
                        .bind("hq", hq)
                        .bind("dc", dataCenterId)
                        .bind("cap", cap))
                .map(row -> new ListingRow(
                        row.getInt("world"),
                        row.getInt("unit_price"),
                        row.getInt("quantity"),
                        row.getBoolean("hq"),
                        row.get("review_time", INSTANT_TIMESTAMP)))
                .all();
    }

    public record ListingRow(int worldId, int unitPrice, int quantity, boolean hq, Instant reviewedAt) {}

    /**
     * Daily buckets of sales for a (world, item, hq) key, going back
     * {@code days} days. Empty days are omitted — the SPA fills gaps
     * client-side because the number of days is small and it's cheaper
     * than generate_series through PG.
     */
    public List<SalesBucket> salesHistory(int itemId, int worldId, boolean hq, int days) {
        return query("""
                SELECT date_trunc('day', sold) AS day,
                       count(*)                AS n,
                       sum(quantity)           AS units,
                       avg(unit_price)::int    AS avg_price,
                       min(unit_price)         AS min_price,
                       max(unit_price)         AS max_price
                  FROM sales
                 WHERE world = :w
                   AND item = :i
                   AND hq = :hq
                   AND sold >= now() - (:days || ' days')::interval
                 GROUP BY day
                 ORDER BY day
                """)
                .single(call().bind("w", worldId)
                        .bind("i", itemId)
                        .bind("hq", hq)
                        .bind("days", days))
                .map(row -> new SalesBucket(
                        row.get("day", INSTANT_TIMESTAMP),
                        row.getInt("n"),
                        row.getInt("units"),
                        row.getInt("avg_price"),
                        row.getInt("min_price"),
                        row.getInt("max_price")))
                .all();
    }

    /**
     * One day's worth of sales aggregate. {@code day} is the truncated
     * bucket start in UTC; prices are in the source column's currency
     * (gil, always).
     */
    public record SalesBucket(Instant day, int sales, int units, int avgPrice, int minPrice, int maxPrice) {}
}
