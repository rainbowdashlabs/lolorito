package de.chojo.lolorito.dao;

import de.chojo.sadu.base.QueryFactory;
import de.chojo.sadu.wrapper.stage.QueryStage;
import de.chojo.sadu.wrapper.stage.UpdateStage;
import de.chojo.universalis.entities.Sale;
import de.chojo.universalis.events.sales.impl.SalesAddEvent;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class Sales extends QueryFactory {
    public Sales(DataSource dataSource) {
        super(dataSource);
    }

    public void logSalesEvent(SalesAddEvent event) {
        QueryStage<Void> builder = builder();
        int worldId = event.world().id();
        int itemId = event.item().id();
        for (Sale sale : event.sales()) {
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
