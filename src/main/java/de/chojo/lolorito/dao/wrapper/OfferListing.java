package de.chojo.lolorito.dao.wrapper;

import de.chojo.sadu.wrapper.util.Row;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.entities.Price;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;

import java.sql.SQLException;
import java.time.LocalDateTime;

public record OfferListing(World world, Item item, boolean hq, Price price, LocalDateTime updated, double factor, int profit) {
    public static OfferListing build(Row row, NameSupplier nameSupplier) throws SQLException {
        World world = Worlds.worldById(row.getInt("world"));
        Item item = Item.build(nameSupplier, row.getInt("Item"));
        boolean hq = row.getBoolean("hq");
        Price price = new Price(row.getInt("unit_price"), row.getInt("quantity"), row.getInt("total"));
        LocalDateTime updated = row.getTimestamp("updated").toLocalDateTime();
        double profitPerc = row.getDouble("factor");
        int profit = row.getInt("profit");
        return new OfferListing(world, item, hq, price, updated, profitPerc, profit);
    }
}
