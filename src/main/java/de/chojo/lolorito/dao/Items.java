package de.chojo.lolorito.dao;

import de.chojo.lolorito.dao.wrapper.ItemListing;
import de.chojo.lolorito.dao.wrapper.ItemStat;
import de.chojo.lolorito.dao.wrapper.Offer;
import de.chojo.sadu.base.QueryFactory;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.World;

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
                                                  unit_price
                                           FROM world_items
                                           WHERE world = ?
                                             AND min_price > ?
                                             AND updated > NOW() - ?::interval
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
                                                AND lu.updated > NOW() - ?::interval
                                                AND data_center = ?),
                             filtered AS (SELECT o.world,
                                                 o.item,
                                                 o.hq,
                                                 o.unit_price,
                                                 o.quantity,
                                                 o.total,
                                                 o.updated,
                                                 home.unit_price::numeric / o.unit_price                      AS factor,
                                                 (home.unit_price * o.quantity) - (o.unit_price * o.quantity) AS profit
                                          FROM homeworld home
                                                   LEFT JOIN other_worlds o
                                                             ON home.item = o.item AND home.hq = o.hq
                                          WHERE (home.unit_price::numeric / o.unit_price) > ? -- factor
                                            AND (home.unit_price * o.quantity) - (o.unit_price * o.quantity) > ?), --profit
                             ranked AS (SELECT RANK() OVER (PARTITION BY world, item, hq ORDER BY profit) AS world_rank,
                                               RANK() OVER (PARTITION BY item, hq ORDER BY profit)        AS global_rank,
                                               world,
                                               item,
                                               hq,
                                               unit_price,
                                               quantity,
                                               total,
                                               updated,
                                               ROUND(factor, 4)                                           AS factor,
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
                               ROUND(factor, 2) AS factor,
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
                                .setInt(filter.limit());
                        }
                ).readRow(row -> ItemListing.build(row, itemNameSupplier))
                .allSync();

        Map<ItemKey, Map<World, WorldListings>> listings = new HashMap<>();
        for (ItemListing listing : itemListings) {
            listings.computeIfAbsent(new ItemKey(listing.hq(), listing.item()), i -> new HashMap<>())
                    .computeIfAbsent(listing.world(), w -> new WorldListings(getStats(w, listing.item()).get(), new ArrayList<>()))
                    .listings()
                    .add(listing);
        }

        List<Offer> offers = new ArrayList<>();
        for (var entry : listings.entrySet()) {
            Optional<ItemStat> stats = getStats(filter.world(), entry.getKey().item());
            if (stats.isEmpty()) continue;
            offers.add(new Offer(stats.get(), listings.get(entry.getKey())));
        }

        return offers;
    }

    private Optional<ItemStat> getStats(World world, Item item) {
        return builder(ItemStat.class)
                .query("""
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
                        FROM world_item_popularity WHERE world = ? AND item = ?;
                       """).parameter(stmt -> stmt.setInt(world.id()).setInt(item.id()))
                .readRow(row -> ItemStat.build(row, itemNameSupplier))
                .firstSync();
    }

    public record WorldListings(ItemStat itemStat, List<ItemListing> listings) {

    }

    private record ItemKey(boolean hq, Item item) {
    }
}
