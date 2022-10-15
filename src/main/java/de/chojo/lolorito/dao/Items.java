package de.chojo.lolorito.dao;

import de.chojo.lolorito.dao.wrapper.ItemListing;
import de.chojo.sadu.base.QueryFactory;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.World;

import javax.sql.DataSource;
import java.util.List;

public class Items extends QueryFactory {

    private final NameSupplier itemNameSupplier;

    public Items(DataSource dataSource, NameSupplier itemNameSupplier) {
        super(dataSource);
        this.itemNameSupplier = itemNameSupplier;
    }

    public List<ItemListing> items(World world, OrderFilter filter) {
        return builder(ItemListing.class)
                .query("""
                        WITH homeworld AS (
                            SELECT l.world,
                                   l.item,
                                   l.hq,
                                   unit_price,
                                   quantity,
                                   total,
                                   review_time
                            FROM listings l
                            LEFT JOIN listings_updated lu ON l.world = lu.world AND l.item = lu.item
                            LEFT JOIN world_item_popularity wip ON l.world = wip.world AND l.item = wip.item AND l.hq = wip.hq
                            WHERE l.world = ?
                              AND unit_price > ?
                              AND lu.updated > NOW() - INTERVAL ?
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
                              AND lu.updated > NOW() - INTERVAL ?
                              AND %s = ?
                        ),
                        filtered AS (
                            SELECT o.world,
                                   o.item,
                                   o.hq,
                                   o.unit_price,
                                   o.quantity,
                                   o.total,
                                   o.updated,
                                   o.unit_price / h.unit_price::numeric AS profit
                            FROM homeworld h LEFT JOIN other_worlds o ON h.item = o.item
                            WHERE o.unit_price / h.unit_price::numeric > ?
                        )
                        SELECT world, item, hq, unit_price, quantity, total, updated, ROUND(profit, 4) AS profit
                        FROM filtered;
                       """, filter.target().columnName())
                .parameter(stmt -> {
                            stmt.setInt(world.id())
                                .setInt(filter.minUnitPrice())
                                .setString(filter.minRefreshDays())
                                .setInt(filter.minPopularity())
                                .setInt(filter.minMarketVolume())
                                .setInt(filter.minInterest())
                                .setInt(filter.minSales())
                                .setInt(filter.minViews())
                                .setInt(world.id())
                                .setString(filter.minRefreshDays());
                            switch (filter.target()) {
                                case REGION -> stmt.setString(world.dataCenter().region().name());
                                case DATA_CENTER -> stmt.setInt(world.dataCenter().id());
                            }
                            stmt.setDouble(filter.minProfitPercentage());
                        }
                ).readRow(row -> ItemListing.build(row, itemNameSupplier))
                .allSync();
    }
}
