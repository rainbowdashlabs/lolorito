package de.chojo.lolorito.dao;

import de.chojo.sadu.base.QueryFactory;
import de.chojo.sadu.wrapper.stage.QueryStage;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.entities.Listing;
import de.chojo.universalis.events.listings.impl.ListingAddEvent;
import de.chojo.universalis.worlds.World;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

public class Listings extends QueryFactory {
    public Listings(DataSource dataSource) {
        super(dataSource);
    }

    public void logListingsEvent(ListingAddEvent event) {
        clearListings(event.item(), event.world());
        addListings(event.item(), event.world(), event.listings());
    }

    public void addListings(Item item, World world, List<Listing> listings) {
        QueryStage<Void> builder = builder();
        int worldId = world.id();
        int itemId = item.id();

        for (Listing listing : listings) {
            boolean hq = listing.meta().hq();
            int unitPrice = listing.price().pricePerUnit();
            int quantity = listing.price().quantity();
            int totalPrice = listing.price().total();
            LocalDateTime reviewTime = listing.lastReviewTime();
            builder.query("""
                          INSERT INTO lolorito.listings(world, item, hq, review_time, unit_price, quantity, total)
                          VALUES(?,?,?,?,?,?,?);
                          """).parameter(stmt -> stmt.setInt(worldId).setInt(itemId).setBoolean(hq)
                                                     .setTimestamp(Timestamp.valueOf(reviewTime))
                                                     .setInt(unitPrice).setInt(quantity).setInt(totalPrice))
                   .append();
        }
        builder.query("""
                      INSERT INTO lolorito.listings_viewed AS l (world, item)
                      VALUES(?,?)
                      ON CONFLICT(world,item, day)
                        DO UPDATE SET count = l.count + 1;
                      """)
               .parameter(stmt -> stmt.setInt(worldId).setInt(itemId))
               .insert()
               .send();
    }

    public void clearListings(Item item, World world) {
        builder().query("""
                                DELETE FROM lolorito.listings WHERE item = ? AND world = ?;
                        """)
                 .parameter(stmt -> stmt.setInt(item.id()).setInt(world.id()))
                 .delete()
                 .send();
    }
}
