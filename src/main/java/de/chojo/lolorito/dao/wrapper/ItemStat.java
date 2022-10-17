package de.chojo.lolorito.dao.wrapper;

import de.chojo.sadu.wrapper.util.Row;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;

import java.sql.SQLException;
import java.time.LocalDateTime;

public record ItemStat(World world,
                       Item item,
                       boolean hq,
                       LocalDateTime updated,
                       double marketVolume,
                       double interest,
                       double popularity,
                       int sales,
                       int views,
                       int minPrice,
                       int avgPrice,
                       int listings,
                       int minSales,
                       int maxSales,
                       int avgSales) {
    public static ItemStat build(Row row, NameSupplier nameSupplier) throws SQLException {
        World world = Worlds.worldById(row.getInt("world"));
        Item item = Item.build(nameSupplier, row.getInt("Item"));
        boolean hq = row.getBoolean("hq");
        double marketVolume = row.getDouble("market_volume");
        double interest = row.getDouble("interest");
        double popularity = row.getDouble("popularity");
        int sales = row.getInt("sales");
        int views = row.getInt("views");
        int minPrice = row.getInt("min_price");
        int avgPrice = row.getInt("avg_price");
        int listings = row.getInt("listings");
        int minSales = row.getInt("min_sales");
        int maxSales = row.getInt("max_sales");
        int avgSales = row.getInt("avg_sales");
        LocalDateTime updated = row.getTimestamp("updated").toLocalDateTime();
        return new ItemStat(world, item, hq, updated, marketVolume, interest, popularity, sales, views, minPrice, avgPrice, listings, minSales, maxSales, avgSales);
    }
}
