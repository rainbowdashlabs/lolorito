/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.ItemListing;
import de.chojo.lolorito.entity.ItemStat;
import de.chojo.lolorito.entity.OfferFilterRow;
import de.chojo.lolorito.entity.OfferListing;
import de.chojo.lolorito.entity.TopFilter;
import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.Call;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.entities.Price;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import org.intellij.lang.annotations.Language;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * SQL for the Discord {@code /check}, {@code /offers get}, and {@code /top}
 * flows. Everything above is assembly — see
 * {@link de.chojo.lolorito.service.ItemsService}. The name supplier is used
 * inline for row → entity mapping.
 */
@Singleton
public class Items {

    private final NameSupplier itemNameSupplier;

    @Inject
    public Items(NameSupplier itemNameSupplier) {
        this.itemNameSupplier = itemNameSupplier;
    }

    /**
     * All fresh listings for {@code itemId} on the given DC, HQ-filtered.
     */
    public List<ItemListing> currentListings(int itemId, int dataCenterId, Boolean hq) {
        @Language("postgresql")
        var q = """
                SELECT
                    world_rank, global_rank, world, item, hq,
                    unit_price, quantity, total, updated
                FROM (
                    SELECT
                        row_number() OVER (PARTITION BY l.world ORDER BY unit_price) AS world_rank,
                        row_number() OVER (ORDER BY unit_price)                      AS global_rank,
                        l.world, l.item, hq, unit_price, quantity, total, updated
                    FROM listings l
                    LEFT JOIN worlds w ON l.world = w.world
                    LEFT JOIN listings_updated u ON l.world = u.world AND l.item = u.item
                    WHERE l.item = :item AND data_center = :dc
                ) a
                WHERE world_rank <= 10 AND global_rank < 100
                  AND ( hq = :hq OR :hq IS NULL )
                ORDER BY world, world_rank
                LIMIT 100
                """;
        return query(q).single(
                        call().bind("item", itemId).bind("dc", dataCenterId).bind("hq", hq))
                .map(this::readItemListing)
                .all();
    }

    /**
     * Offer-explorer arbitrage rows for the caller's saved filter. The
     * target column and scope value come from a static enum allowlist
     * ({@code offer_filter.target}), so the substitution is safe.
     */
    public List<OfferListing> arbitrageListings(OfferFilterRow filter, World homeWorld) {
        var target = filter.targetEnum();
        @Language("postgresql")
        var q = """
                WITH homeworld AS (SELECT world, item, hq, unit_price,
                                          sales / 7 AS daily_sales
                                   FROM world_items
                                   WHERE world = :home_world
                                     AND min_price > :unit_price_floor
                                     AND updated > now() - (:refresh_home)::INTERVAL
                                     AND popularity > :popularity
                                     AND market_volume > :market_volume
                                     AND interest > :interest
                                     AND sales > :sales
                                     AND views > :views),
                     other_worlds AS (SELECT l.world, l.item, hq, unit_price, quantity, total, lu.updated
                                      FROM listings l
                                      LEFT JOIN worlds w  ON l.world = w.world
                                      LEFT JOIN listings_updated lu ON l.world = lu.world AND l.item = lu.item
                                      WHERE l.world != :home_world
                                        AND lu.updated > now() - (:refresh_other)::INTERVAL
                                        AND %s = :scope),
                     other_worlds_stats AS (SELECT o.item, o.hq,
                                                   min(o.unit_price) AS min_price,
                                                   max(o.unit_price) AS max_price,
                                                   sum(o.quantity)   AS volume
                                            FROM other_worlds o GROUP BY o.item, o.hq),
                     effective_profit AS (SELECT o.item, o.hq,
                                                 least(daily_sales, volume) * home.unit_price - least(daily_sales, volume) * min_price AS max_effective_profit
                                          FROM other_worlds_stats o
                                          LEFT JOIN homeworld home ON o.item = home.item AND o.hq = home.hq),
                     filtered AS (SELECT o.world, o.item, o.hq, o.unit_price, o.quantity, o.total, o.updated,
                                         home.unit_price::NUMERIC / o.unit_price                     AS factor,
                                         (home.unit_price * o.quantity) - (o.unit_price * o.quantity) AS profit
                                  FROM homeworld home
                                  LEFT JOIN other_worlds o ON home.item = o.item AND home.hq = o.hq
                                  LEFT JOIN other_worlds_stats s ON o.item = s.item AND o.hq = s.hq
                                  LEFT JOIN effective_profit p ON o.item = p.item AND o.hq = p.hq
                                  WHERE (home.unit_price::NUMERIC / o.unit_price) > :factor
                                    AND (home.unit_price * o.quantity) - (o.unit_price * o.quantity) > :profit
                                    AND max_effective_profit > :effective_profit),
                     ranked AS (SELECT rank() OVER (PARTITION BY world, item, hq ORDER BY profit) AS world_rank,
                                       rank() OVER (PARTITION BY item, hq ORDER BY profit)        AS global_rank,
                                       world, item, hq, unit_price, quantity, total, updated,
                                       round(factor, 4) AS factor, profit
                                FROM filtered
                                ORDER BY profit DESC)
                SELECT r.world, r.item, r.hq, r.unit_price, quantity, total, r.updated,
                       round(factor, 2) AS factor, profit
                FROM ranked r
                WHERE world_rank <= 10 AND global_rank < 100
                ORDER BY profit DESC
                LIMIT :lim;
                """.formatted(target.columnName());

        Call bound = call().bind("home_world", homeWorld.id())
                .bind("unit_price_floor", filter.unitPrice())
                .bind("refresh_home", "%d HOURS".formatted(filter.refreshHours()))
                .bind("popularity", filter.popularity())
                .bind("market_volume", filter.marketVolume())
                .bind("interest", filter.interest())
                .bind("sales", filter.sales())
                .bind("views", filter.views())
                .bind("refresh_other", "%d HOURS".formatted(filter.refreshHours()))
                .bind("factor", filter.factor())
                .bind("profit", filter.profit())
                .bind("effective_profit", filter.effectiveProfit())
                .bind("lim", filter.offerLimit());
        bound = switch (target) {
            case REGION -> bound.bind("scope", homeWorld.dataCenter().region().name());
            case DATA_CENTER -> bound.bind("scope", homeWorld.dataCenter().id());
        };

        return query(q).single(bound).map(this::readOfferListing).all();
    }

    /**
     * Pre-aggregated stats for one {@code (world, item, hq)}. Sentinel row on miss.
     */
    public ItemStat stats(World world, Item item, Boolean hq) {
        @Language("postgresql")
        var q = """
                SELECT world, item,
                       min(hq::INT)::BOOLEAN AS hq,
                       avg(market_volume) AS market_volume,
                       avg(interest) AS interest,
                       avg(popularity) AS popularity,
                       sum(sales) AS sales,
                       max(views) AS views,
                       min(min_price) AS min_price,
                       avg(avg_price) AS avg_price,
                       sum(listings) AS listings,
                       max(updated) AS updated,
                       min(min_sales) AS min_sales,
                       max(max_sales) AS max_sales,
                       avg(avg_sales) AS avg_sales
                FROM world_item_popularity
                WHERE world = :world AND item = :item AND (hq = :hq OR :hq IS NULL)
                GROUP BY world, item;
                """;
        return query(q).single(
                        call().bind("world", world.id()).bind("item", item.id()).bind("hq", hq))
                .map(this::readItemStat)
                .first()
                .orElseGet(() -> ItemStat.empty(world, item, hq));
    }

    /**
     * Aggregated top-100 view for {@code /top}.
     */
    public List<ItemStat> top(TopFilter topFilter, int scopeId) {
        String scopeColumn =
                switch (topFilter.searchScope()) {
                    case WORLD -> "world";
                    case DATACENTER -> "data_center";
                };
        @Language("postgresql")
        var q = """
                SELECT item, hq,
                    round(avg(market_volume), 2) AS market_volume,
                    round(avg(interest), 2)      AS interest,
                    round(avg(popularity), 2)    AS popularity,
                    round(sum(sales), 2)         AS sales,
                    round(sum(views), 2)         AS views,
                    round(avg(min_price), 2)     AS min_price,
                    round(avg(avg_price), 2)     AS avg_price,
                    round(sum(listings), 2)      AS listings,
                    round(avg(min_sales), 2)     AS min_sales,
                    round(avg(avg_sales), 2)     AS avg_sales,
                    round(avg(max_sales), 2)     AS max_sales
                FROM world_item_popularity s
                    LEFT JOIN worlds w ON s.world = w.world
                WHERE w.%s = :scope
                  AND ( hq = :hq OR :hq IS NULL )
                  AND ( sales >= :min_sales OR :min_sales IS NULL )
                  AND ( market_volume >= :min_market_volume OR :min_market_volume IS NULL )
                  AND ( interest >= :min_interest OR :min_interest IS NULL )
                  AND ( popularity >= :min_popularity OR :min_popularity IS NULL )
                  AND ( min_sales >= :min_price_floor OR :min_price_floor IS NULL )
                  AND ( avg_sales >= :min_avg_price OR :min_avg_price IS NULL )
                GROUP BY item, hq
                ORDER BY %s DESC NULLS LAST
                LIMIT 100;
                """.formatted(scopeColumn, topFilter.order().name().toLowerCase());

        return query(q).single(call().bind("scope", scopeId)
                        .bind("hq", topFilter.hq())
                        .bind("min_sales", topFilter.minSales())
                        .bind("min_market_volume", topFilter.minMarketVolume())
                        .bind("min_interest", topFilter.minInterest())
                        .bind("min_popularity", topFilter.minPopularity())
                        .bind("min_price_floor", topFilter.minPrice())
                        .bind("min_avg_price", topFilter.minAvgPrice()))
                .map(this::readItemStatNoWorld)
                .all();
    }

    // --- Row mappers -------------------------------------------------------

    private ItemListing readItemListing(Row row) throws SQLException {
        World world = Worlds.worldById(row.getInt("world"));
        Item item = Item.build(itemNameSupplier, row.getInt("item"));
        boolean hq = row.getBoolean("hq");
        Price price = new Price(row.getInt("unit_price"), row.getInt("quantity"), row.getInt("total"));
        Instant updated = row.get("updated", INSTANT_TIMESTAMP);
        return new ItemListing(world, item, hq, price, updated);
    }

    private OfferListing readOfferListing(Row row) throws SQLException {
        World world = Worlds.worldById(row.getInt("world"));
        Item item = Item.build(itemNameSupplier, row.getInt("Item"));
        boolean hq = row.getBoolean("hq");
        Price price = new Price(row.getInt("unit_price"), row.getInt("quantity"), row.getInt("total"));
        Instant updated = row.get("updated", INSTANT_TIMESTAMP);
        double factor = row.getDouble("factor");
        int profit = row.getInt("profit");
        return new OfferListing(world, item, hq, price, updated, factor, profit);
    }

    private ItemStat readItemStat(Row row) throws SQLException {
        World world = Worlds.worldById(row.getInt("world"));
        Item item = Item.build(itemNameSupplier, row.getInt("Item"));
        return new ItemStat(
                world,
                item,
                row.getBoolean("hq"),
                row.get("updated", INSTANT_TIMESTAMP),
                row.getDouble("market_volume"),
                row.getDouble("interest"),
                row.getDouble("popularity"),
                row.getInt("sales"),
                row.getInt("views"),
                row.getInt("min_price"),
                row.getInt("avg_price"),
                row.getInt("listings"),
                row.getInt("min_sales"),
                row.getInt("max_sales"),
                row.getInt("avg_sales"));
    }

    private ItemStat readItemStatNoWorld(Row row) throws SQLException {
        Item item = Item.build(itemNameSupplier, row.getInt("item"));
        return new ItemStat(
                null,
                item,
                row.getBoolean("hq"),
                null,
                row.getDouble("market_volume"),
                row.getDouble("interest"),
                row.getDouble("popularity"),
                row.getInt("sales"),
                row.getInt("views"),
                row.getInt("min_price"),
                row.getInt("avg_price"),
                row.getInt("listings"),
                row.getInt("min_sales"),
                row.getInt("max_sales"),
                row.getInt("avg_sales"));
    }
}
