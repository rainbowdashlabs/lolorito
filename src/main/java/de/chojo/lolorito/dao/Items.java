package de.chojo.lolorito.dao;

import de.chojo.lolorito.dao.wrapper.ItemListing;
import de.chojo.lolorito.dao.wrapper.ItemStat;
import de.chojo.lolorito.dao.wrapper.Offer;
import de.chojo.sadu.base.QueryFactory;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.World;
import org.intellij.lang.annotations.Language;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Items extends QueryFactory {

    private final NameSupplier itemNameSupplier;

    public Items(DataSource dataSource, NameSupplier itemNameSupplier) {
        super(dataSource);
        this.itemNameSupplier = itemNameSupplier;
    }

    public List<Offer> bestOffers(BotUser botUser) {
        OfferFilter filter = botUser.offerFilter();
        if (filter.world().dataCenter() == null) {
            return Collections.emptyList();
        }
        List<ItemListing> itemListings = builder(ItemListing.class)
                .query("""
                         WITH homeworld AS (SELECT world,
                                                   item,
                                                   hq,
                                                   unit_price,
                                                   sales / 7 AS daily_sales,
                                                   sales / 7 * unit_price AS daily_win
                                            FROM world_items
                                            WHERE world = ?
                                              AND min_price > ?
                                              AND updated > now() - ?::INTERVAL
                                              AND popularity > ?
                                              AND market_volume > ?
                                              AND interest > ?
                                              AND sales > ?
                                              AND views > ?
                                              ),
                              other_worlds AS (SELECT l.world,
                                                      l.item,
                                                      hq,
                                                      unit_price,
                                                      quantity,
                                                      total,
                                                      lu.updated
                                               FROM listings l
                                                        LEFT JOIN worlds w
                                                                  ON l.world = w.world
                                                        LEFT JOIN listings_updated lu
                                                                  ON l.world = lu.world AND l.item = lu.item
                                               WHERE l.world != ?
                                                 AND lu.updated > now() - ?::INTERVAL
                                                 AND data_center = ?),
                              other_worlds_stats AS(SELECT o.item,
                                                           o.hq,
                                                           min(o.unit_price) AS min_price,
                                                           max(o.unit_price) AS max_price,
                                                           sum(o.quantity) AS volume
                                                    FROM other_worlds o
                                                    GROUP BY o.item, o.hq
                                                 ),
                              effective_profit AS (SELECT o.item,
                                                          o.hq,
                                                          least(daily_sales, volume) * home.unit_price - least(daily_sales, volume) * min_price AS max_effective_profit,
                                                          least(daily_sales, volume) * home.unit_price - least(daily_sales, volume) * max_price AS min_effective_profit
                                                   FROM other_worlds_stats o
                                                   LEFT JOIN homeworld home ON o.item = home.item AND o.hq = home.hq
                              ),
                              filtered AS (SELECT o.world,
                                                  o.item,
                                                  o.hq,
                                                  o.unit_price,
                                                  o.quantity,
                                                  o.total,
                                                  o.updated,
                                                  home.unit_price::NUMERIC / o.unit_price                      AS factor,
                                                  (home.unit_price * o.quantity) - (o.unit_price * o.quantity) AS profit
                                           FROM homeworld home
                                                    LEFT JOIN other_worlds o
                                                              ON home.item = o.item AND home.hq = o.hq
                                                    LEFT JOIN other_worlds_stats s ON o.item = s.item AND o.hq = s.hq
                                                    LEFT JOIN effective_profit p ON o.item = p.item AND o.hq = p.hq
                                           WHERE (home.unit_price::NUMERIC / o.unit_price) > ? -- factor
                                             AND (home.unit_price * o.quantity) - (o.unit_price * o.quantity) > ? -- profit
                                             AND max_effective_profit > ?), --profit
                              ranked AS (SELECT rank() OVER (PARTITION BY world, item, hq ORDER BY profit) AS world_rank,
                                                rank() OVER (PARTITION BY item, hq ORDER BY profit)        AS global_rank,
                                                world,
                                                item,
                                                hq,
                                                unit_price,
                                                quantity,
                                                total,
                                                updated,
                                                round(factor, 4)                                           AS factor,
                                                profit
                                         FROM filtered
                                         ORDER BY profit DESC)
                         SELECT r.world,
                                r.item,
                                r.hq,
                                r.unit_price,
                                quantity,
                                total,
                                r.updated,
                                round(factor, 2) AS factor,
                                profit
                         FROM ranked r
                         WHERE world_rank <= 10
                           AND global_rank < 100
                         ORDER BY profit DESC
                         LIMIT ?;
                        """, filter.target().columnName())
                .parameter(stmt -> {
                            stmt.setInt(filter.world().id())
                                    .setInt(filter.unitPrice())
                                    .setString(filter.refreshHours())
                                    .setDouble(filter.popularity())
                                    .setDouble(filter.marketVolume())
                                    .setDouble(filter.interest())
                                    .setInt(filter.sales())
                                    .setInt(filter.views())
                                    .setInt(filter.world().id())
                                    .setString(filter.refreshHours());
                            switch (filter.target()) {
                                case REGION -> stmt.setString(filter.world().dataCenter().region().name());
                                case DATA_CENTER -> stmt.setInt(filter.world().dataCenter().id());
                            }
                            stmt.setDouble(filter.factor())
                                    .setInt(filter.profit())
                                    .setInt(filter.effectiveProfit())
                                    .setInt(filter.limit());
                        }
                ).readRow(row -> ItemListing.build(row, itemNameSupplier))
                .allSync();

        Map<ItemKey, Map<World, WorldListings>> listings = new HashMap<>();
        for (ItemListing listing : itemListings) {
            listings.computeIfAbsent(new ItemKey(listing.hq(), listing.item()), i -> new HashMap<>())
                    .computeIfAbsent(listing.world(), w -> new WorldListings(getStats(w, listing.item(), listing.hq()).get(), new ArrayList<>()))
                    .listings()
                    .add(listing);
        }

        List<Offer> offers = new ArrayList<>();
        for (var entry : listings.entrySet()) {
            Optional<ItemStat> stats = getStats(filter.world(), entry.getKey().item(), entry.getKey().hq());
            if (stats.isEmpty()) continue;
            offers.add(new Offer(stats.get(), listings.get(entry.getKey())));
        }

        return offers;
    }

    private Optional<ItemStat> getStats(World world, Item item, boolean hq) {
        @Language("postgresql")
        var query = """
                 SELECT world,
                        item,
                        hq,
                        market_volume,
                        interest,
                        popularity,
                        sales,
                        views,
                        min_price,
                        avg_price,
                        listings,
                        updated,
                        min_sales,
                        max_sales,
                        avg_sales
                 FROM world_item_popularity WHERE world = ? AND item = ? AND hq = ?;
                """;
        return builder(ItemStat.class)
                .query(query).parameter(stmt -> stmt.setInt(world.id()).setInt(item.id()).setBoolean(hq))
                .readRow(row -> ItemStat.build(row, itemNameSupplier))
                .firstSync();
    }

    public List<ItemStat> topItems(BotUser botUser, TopFilter topFilter) {
        OfferFilter offerFilter = botUser.offerFilter();
        @Language("postgresql")
        var query = """
                SELECT
                    item,
                    hq,
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
                FROM
                    lolorito.world_item_popularity s
                        LEFT JOIN lolorito.worlds w
                        ON s.world = w.world
                WHERE w.%s = ?
                  AND ( hq = ? OR ? IS NULL )
                  AND ( sales >= ? OR ? IS NULL )
                  AND ( market_volume >= ? OR ? IS NULL )
                  AND ( interest >= ? OR ? IS NULL )
                  AND ( popularity >= ? OR ? IS NULL )
                  AND ( min_sales >= ? OR ? IS NULL )
                  AND ( avg_sales >= ? OR ? IS NULL )
                GROUP BY item, hq
                ORDER BY %s DESC NULLS LAST
                LIMIT 100;""";
        int scopeId = switch (topFilter.searchScope()) {
            case WORLD -> offerFilter.world().id();
            case DATACENTER -> offerFilter.world().dataCenter().id();
        };

        String scopeColumn = switch (topFilter.searchScope()) {
            case WORLD -> "world";
            case DATACENTER -> "data_center";
        };

        return builder(ItemStat.class)
                .query(query, scopeColumn, topFilter.order().name().toLowerCase())
                .parameter(stmt -> stmt
                        .setInt(scopeId)
                        .setBoolean(topFilter.hq()).setBoolean(topFilter.hq())
                        .setInt(topFilter.minSales()).setInt(topFilter.minSales())
                        .setDouble(topFilter.minMarketVolume()).setDouble(topFilter.minMarketVolume())
                        .setDouble(topFilter.minInterest()).setDouble(topFilter.minInterest())
                        .setDouble(topFilter.minPopularity()).setDouble(topFilter.minPopularity())
                        .setDouble(topFilter.minPrice()).setDouble(topFilter.minPrice())
                        .setDouble(topFilter.minAvgPrice()).setDouble(topFilter.minAvgPrice())
                ).readRow(row -> ItemStat.buildWithoutWorld(row, itemNameSupplier))
                .allSync();
    }

    public record WorldListings(ItemStat itemStat, List<ItemListing> listings) {

    }

    private record ItemKey(boolean hq, Item item) {
    }
}
