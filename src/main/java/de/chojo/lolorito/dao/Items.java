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
                        WITH homeworld AS (
                            SELECT l.world,
                                   l.item,
                                   l.hq,
                                   l.min_price AS unit_price
                            FROM world_item_listings l
                            LEFT JOIN listings_updated lu ON l.world = lu.world AND l.item = lu.item
                            LEFT JOIN world_item_popularity wip ON l.world = wip.world AND l.item = wip.item AND l.hq = wip.hq
                            WHERE l.world = ?
                              AND l.min_price > ?
                              AND lu.updated > NOW() - ?::INTERVAL
                              AND popularity > ?
                              AND market_volume > ?
                              AND interest > ?
                              AND sales > ?
                              AND views > ?
                            ),
                        other_worlds AS (
                            SELECT l.world,
                                   l.item,
                                   hq,
                                   unit_price,
                                   quantity,
                                   total,
                                   lu.updated
                            FROM listings l
                            LEFT JOIN worlds w ON l.world = w.world
                            LEFT JOIN listings_updated lu ON l.world = lu.world AND l.item = lu.item
                            WHERE l.world != ?
                              AND lu.updated > NOW() - ?::INTERVAL
                              AND data_center = ?
                        ),
                        filtered AS (
                            SELECT o.world,
                                   o.item,
                                   o.hq,
                                   o.unit_price,
                                   o.quantity,
                                   o.total,
                                   o.updated,
                                   h.unit_price::numeric / o.unit_price * 100 AS profit_perc,
                                   (h.unit_price * o.quantity) - (o.unit_price * o.quantity) as profit
                            FROM homeworld h LEFT JOIN other_worlds o ON h.item = o.item AND h.hq = o.hq
                            WHERE (h.unit_price::numeric / o.unit_price) * 100 > ?
                              AND (h.unit_price * o.quantity) - (o.unit_price * o.quantity) > ?
                        )
                        SELECT world, item, hq, unit_price, quantity, total, updated, ROUND(profit_perc, 4) AS profit_perc, profit
                        FROM filtered
                        ORDER BY profit DESC
                        LIMIT ?;
                       """, filter.target().columnName())
                .parameter(stmt -> {
                            stmt.setInt(filter.world().id())
                                .setInt(filter.minUnitPrice())
                                .setString(filter.minRefreshHours())
                                .setInt(filter.minPopularity())
                                .setInt(filter.minMarketVolume())
                                .setInt(filter.minInterest())
                                .setInt(filter.minSales())
                                .setInt(filter.minViews())
                                .setInt(filter.world().id())
                                .setString(filter.minRefreshHours());
                            switch (filter.target()) {
                                case REGION -> stmt.setString(filter.world().dataCenter().region().name());
                                case DATA_CENTER -> stmt.setInt(filter.world().dataCenter().id());
                            }
                            stmt.setDouble(filter.minProfitPercentage())
                                .setInt(filter.minProfit())
                                .setInt(filter.limit());
                        }
                ).readRow(row -> ItemListing.build(row, itemNameSupplier))
                .allSync();

        Map<ItemKey, Map<World, List<ItemListing>>> listings = new HashMap<>();
        for (ItemListing listing : itemListings) {
            listings.computeIfAbsent(new ItemKey(listing.hq(), listing.item()), i -> new HashMap<>())
                    .computeIfAbsent(listing.world(), w -> new ArrayList<>())
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
                               updated
                        FROM world_item_popularity WHERE world = ? AND item = ?;
                       """).parameter(stmt -> stmt.setInt(world.id()).setInt(item.id()))
                .readRow(row -> ItemStat.build(row, itemNameSupplier))
                .firstSync();
    }

    private record ItemKey(boolean hq, Item item){}
}
