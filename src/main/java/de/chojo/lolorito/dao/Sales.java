package de.chojo.lolorito.dao;

import de.chojo.sadu.base.QueryFactory;
import de.chojo.sadu.wrapper.stage.QueryStage;
import de.chojo.sadu.wrapper.stage.UpdateStage;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.entities.Sale;
import de.chojo.universalis.worlds.World;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;

public class Sales extends QueryFactory {
    public Sales(DataSource dataSource) {
        super(dataSource);
    }

    public void addSales(World world, Item item, Collection<Sale> sales) {
        QueryStage<Void> builder = builder();
        int worldId = world.id();
        int itemId = item.id();
        for (Sale sale : sales) {
            int unitPrice = sale.price().pricePerUnit();
            int quantity = sale.price().quantity();
            int total = sale.price().total();
            boolean hq = sale.hq();
            LocalDateTime timestamp = sale.timestamp();
            builder.query("""
                          INSERT INTO sales(world, item, hq, sold, unit_price, quantity, total)
                          VALUES (?,?,?,?,?,?,?)
                          """)
                   .parameter(stmt -> stmt.setInt(worldId).setInt(itemId).setBoolean(hq)
                                          .setTimestamp(Timestamp.valueOf(timestamp))
                                          .setInt(unitPrice).setInt(quantity).setInt(total))
                   .append();
        }
        ((UpdateStage) builder).send();
    }
}
