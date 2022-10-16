package de.chojo.lolorito.dao;

import de.chojo.sadu.base.QueryFactory;
import net.dv8tion.jda.api.entities.User;

import javax.sql.DataSource;

public class BotUser extends QueryFactory {
    private final User user;

    public BotUser(User user, DataSource factoryHolder) {
        super(factoryHolder);
        this.user = user;
    }

    public OfferFilter offerFilter() {
        return builder(OfferFilter.class)
                .query("""
                        SELECT * FROM offer_filter WHERE user_id = ?
                       """)
                .parameter(stmt -> stmt.setLong(user.getIdLong()))
                .readRow(row -> OfferFilter.build(this, row))
                .firstSync()
                .orElseGet(() -> new OfferFilter(this));
    }

    public long userId() {
        return user.getIdLong();
    }
}
