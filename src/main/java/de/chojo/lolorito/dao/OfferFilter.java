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
    private int minUnitPrice = 1000;
    private int minProfitPercentage = 10;
    private int minRefreshHours = 1;
    private int minPopularity = 0;
    private int minMarketVolume = 0;
    private int minInterest = 0;
    private int minSales = 0;
    private int minViews = 0;
    private Target target = Target.DATA_CENTER;
    private int minProfit = 100;

    public OfferFilter(BotUser user) {
        super(user);
        this.user = user;
    }

    public OfferFilter(BotUser botUser, World world, int limit, int minUnitPrice, int minProfitPercentage, int minRefreshHours, int minPopularity, int minMarketVolume, int minInterest, int minSales, int minViews, int minProfit, Target target) {
        this(botUser);
        this.world = world;
        this.limit = limit;
        this.minUnitPrice = minUnitPrice;
        this.minProfitPercentage = minProfitPercentage;
        this.minRefreshHours = minRefreshHours;
        this.minPopularity = minPopularity;
        this.minMarketVolume = minMarketVolume;
        this.minInterest = minInterest;
        this.minSales = minSales;
        this.minViews = minViews;
        this.minProfit = minProfit;
        this.target = target;
    }

    public int limit() {
        return limit;
    }

    public World world() {
        return world;
    }

    public int minUnitPrice() {
        return minUnitPrice;
    }

    public String minRefreshHours() {
        return "%d HOURS".formatted(minRefreshHours);
    }

    public Target target() {
        return target;
    }

    public double minProfitPercentage() {
        return minProfitPercentage;
    }

    public int minPopularity() {
        return minPopularity;
    }

    public int minMarketVolume() {
        return minMarketVolume;
    }

    public int minInterest() {
        return minInterest;
    }

    public int minSales() {
        return minSales;
    }

    public int minViews() {
        return minViews;
    }

    public void minProfit(int minProfit) {
        if (set("min_profit", stmt -> stmt.setInt(minProfit))) {
            this.minProfit = minProfit;
        }
    }

    public void world(World world) {
        if (set("world", stmt -> stmt.setInt(world.id()))) {
            this.world = world;
        }
    }

    public void minUnitPrice(int minUnitPrice) {
        if (set("min_unit_price", stmt -> stmt.setInt(minUnitPrice))) {
            this.minUnitPrice = minUnitPrice;
        }
    }

    public void limit(int limit) {
        if (set("offer_limit", stmt -> stmt.setInt(limit))) {
            this.limit = limit;
        }
    }

    public void minProfitPercentage(int minProfitPercentage) {
        if (set("min_profit_percentage", stmt -> stmt.setInt(minProfitPercentage))) {
            this.minProfitPercentage = minProfitPercentage;
        }
    }

    public void minRefreshHours(int minRefreshHours) {
        if (set("min_refresh_hours", stmt -> stmt.setInt(minRefreshHours))) {
            this.minRefreshHours = minRefreshHours;
        }
    }

    public void minPopularity(int minPopularity) {
        if (set("min_popularity", stmt -> stmt.setInt(minPopularity))) {
            this.minPopularity = minPopularity;
        }
    }

    public void minMarketVolume(int minMarketVolume) {
        if (set("min_market_volume", stmt -> stmt.setInt(minMarketVolume))) {
            this.minMarketVolume = minMarketVolume;
        }
    }

    public void minInterest(int minInterest) {
        if (set("world", stmt -> stmt.setInt(minInterest))) {
            this.minInterest = minInterest;
        }
    }

    public void minSales(int minSales) {
        if (set("world", stmt -> stmt.setInt(minSales))) {
            this.minSales = minSales;
        }
    }

    public void minViews(int minViews) {
        if (set("world", stmt -> stmt.setInt(minViews))) {
            this.minViews = minViews;
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
        int minUnitPrice = row.getInt("min_unit_price");
        int minProfitPercentage = row.getInt("min_profit_percentage");
        int minProfit = row.getInt("min_profit");
        int minRefreshHours = row.getInt("min_refresh_hours");
        int minPopularity = row.getInt("min_popularity");
        int minMarketVolume = row.getInt("min_market_volume");
        int minInterest = row.getInt("min_interest");
        int minSales = row.getInt("min_sales");
        int minViews = row.getInt("min_views");
        Target target = Target.valueOf(row.getString("target"));
        return new OfferFilter(botUser, world, limit, minUnitPrice, minProfitPercentage, minRefreshHours,
                minPopularity, minMarketVolume, minInterest, minSales, minViews, minProfit, target);
    }

    public MessageEmbed embed() {
        String descr = """
                       World: %s
                       Limit: %d
                       Unit Price: >%d
                       Profit %%: >%d%%
                       Profit: >%d
                       Freshness: %d hours
                       Popularity: >%d
                       Market Volume: >%d
                       Interest: >%d
                       Sales: >%d
                       Views: >%d
                       Target: %s
                       """.formatted(world.name(), limit, minUnitPrice, minProfitPercentage,minProfit, minRefreshHours, minPopularity,
                minMarketVolume, minInterest, minSales, minViews, target.name()).stripIndent();
        return new EmbedBuilder()
                .setTitle("Filter")
                .setDescription(descr)
                .build();
    }

    public int minProfit() {
        return minProfit;
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
