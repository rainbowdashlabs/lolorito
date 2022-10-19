package de.chojo.lolorito.dao;

import de.chojo.sadu.base.QueryFactory;
import de.chojo.sadu.exceptions.ThrowingConsumer;
import de.chojo.sadu.wrapper.util.ParamBuilder;
import de.chojo.sadu.wrapper.util.Row;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.sql.SQLException;

public class OfferFilter extends QueryFactory {
    private final BotUser user;
    private World world = World.of("", -1, null);
    private int limit = 1000;
    private int unitPrice = 1000;
    private double factor = 2;
    private int refreshHours = 1;
    private double popularity = 0;
    private double marketVolume = 0;
    private double interest = 0;
    private int sales = 0;
    private int views = 0;
    private Target target = Target.DATA_CENTER;
    private int profit = 100;

    public OfferFilter(BotUser user) {
        super(user);
        this.user = user;
    }

    public OfferFilter(BotUser botUser, World world, int limit, int unitPrice, int factor, int refreshHours, int popularity, int marketVolume, int interest, int sales, int views, int profit, Target target) {
        this(botUser);
        this.world = world;
        this.limit = limit;
        this.unitPrice = unitPrice;
        this.factor = factor;
        this.refreshHours = refreshHours;
        this.popularity = popularity;
        this.marketVolume = marketVolume;
        this.interest = interest;
        this.sales = sales;
        this.views = views;
        this.profit = profit;
        this.target = target;
    }

    public int limit() {
        return limit;
    }

    public World world() {
        return world;
    }

    public int unitPrice() {
        return unitPrice;
    }

    public String refreshHours() {
        return "%d HOURS".formatted(refreshHours);
    }

    public Target target() {
        return target;
    }

    public double factor() {
        return factor;
    }

    public double popularity() {
        return popularity;
    }

    public double marketVolume() {
        return marketVolume;
    }

    public double interest() {
        return interest;
    }

    public int sales() {
        return sales;
    }

    public int views() {
        return views;
    }

    public void profit(int profit) {
        if (set("profit", stmt -> stmt.setInt(profit))) {
            this.profit = profit;
        }
    }

    public void world(World world) {
        if (set("world", stmt -> stmt.setInt(world.id()))) {
            this.world = world;
        }
    }

    public void unitPrice(int unitPrice) {
        if (set("unit_price", stmt -> stmt.setInt(unitPrice))) {
            this.unitPrice = unitPrice;
        }
    }

    public void limit(int limit) {
        if (set("offer_limit", stmt -> stmt.setInt(limit))) {
            this.limit = limit;
        }
    }

    public void factor(double factor) {
        if (set("factor", stmt -> stmt.setDouble(factor))) {
            this.factor = factor;
        }
    }

    public void refreshHours(int refreshHours) {
        if (set("refresh_hours", stmt -> stmt.setInt(refreshHours))) {
            this.refreshHours = refreshHours;
        }
    }

    public void popularity(double popularity) {
        if (set("popularity", stmt -> stmt.setDouble(popularity))) {
            this.popularity = popularity;
        }
    }

    public void marketVolume(double marketVolume) {
        if (set("market_volume", stmt -> stmt.setDouble(marketVolume))) {
            this.marketVolume = marketVolume;
        }
    }

    public void interest(double interest) {
        if (set("interest", stmt -> stmt.setDouble(interest))) {
            this.interest = interest;
        }
    }

    public void sales(int sales) {
        if (set("sales", stmt -> stmt.setInt(sales))) {
            this.sales = sales;
        }
    }

    public void views(int views) {
        if (set("views", stmt -> stmt.setInt(views))) {
            this.views = views;
        }
    }

    public void target(Target target) {
        if (set("target", stmt -> stmt.setString(target.name()))) {
            this.target = target;
        }
    }

    private boolean set(String column, ThrowingConsumer<ParamBuilder, SQLException> apply) {
        return builder()
                .query("""
                        INSERT INTO offer_filter(user_id, %s) VALUES(?,?)
                        ON CONFLICT(user_id)
                            DO UPDATE SET %s = excluded.%s
                       """, column, column, column)
                .parameter(stmt -> {
                    stmt.setLong(user.userId());
                    apply.accept(stmt);
                })
                .insert()
                .sendSync()
                .changed();
    }

    public static OfferFilter build(BotUser botUser, Row row) throws SQLException {
        World world = Worlds.worldById(row.getInt("world"));
        int limit = row.getInt("offer_limit");
        int unitPrice = row.getInt("unit_price");
        int factor = row.getInt("factor");
        int profit = row.getInt("profit");
        int refreshHours = row.getInt("refresh_hours");
        int popularity = row.getInt("popularity");
        int marketVolume = row.getInt("market_volume");
        int interest = row.getInt("interest");
        int sales = row.getInt("sales");
        int views = row.getInt("views");
        Target target = Target.valueOf(row.getString("target"));
        return new OfferFilter(botUser, world, limit, unitPrice, factor, refreshHours,
                popularity, marketVolume, interest, sales, views, profit, target);
    }

    public MessageEmbed embed() {
        String descr = """
                       ```
                       World:         %s
                       Limit:         %d
                       Unit Price:    %d
                       Factor:        %.2f
                       Profit:        %d
                       Freshness:     %d hours
                       Popularity:    %.2f
                       Market Volume: %.2f
                       Interest:      %.2f
                       Sales:         %d
                       Views:         %d
                       Target:        %s
                       ```
                       """.formatted(world.name(), limit, unitPrice, factor, profit, refreshHours, popularity,
                marketVolume, interest, sales, views, target.name()).stripIndent();
        return new EmbedBuilder()
                .setTitle("Filter")
                .setDescription(descr)
                .build();
    }

    public int profit() {
        return profit;
    }

    public enum Target {
        REGION("region_name"), DATA_CENTER("data_center");

        private final String columnName;

        Target(String columnName) {
            this.columnName = columnName;
        }

        public String columnName() {
            return columnName;
        }
    }
}
