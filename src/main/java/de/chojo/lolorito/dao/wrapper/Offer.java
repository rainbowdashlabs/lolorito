package de.chojo.lolorito.dao.wrapper;

import de.chojo.jdautil.text.TextFormatting;
import de.chojo.lolorito.dao.Items.WorldListings;
import de.chojo.universalis.entities.Price;
import de.chojo.universalis.worlds.World;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.TimeFormat;

import java.time.ZoneId;
import java.util.Map;

public record Offer(ItemStat stats, Map<World, WorldListings> offers) {
    public String universalisUrl() {
        return "https://universalis.app/market/%d".formatted(stats.item().id());
    }

    public MessageEmbed embed() {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(stats.item().name().english(),universalisUrl());
        String description = """
                       ```
                       High Quality:  %s
                       Market Volume: %.02f%%
                       Interest:      %.02f%%
                       Popularity:    %.02f%%
                       Sales:         %,d
                       Views:         %,d
                       Min Price:     %,d
                       Average Price: %,d
                       Min Sold:      %,d
                       Average Sold:  %,d
                       Max Sold:      %,d
                       Listings:      %,d
                       ```
                       """.formatted(stats.hq(), stats.marketVolume(), stats.interest(), stats.popularity(),
                stats.sales(), stats.views(), stats.minPrice(), stats.avgPrice(), stats.minSales(),
                stats.avgSales(), stats.maxSales(), stats.listings());
        builder.setDescription(description);
        String comm = """
                           ```
                           Buy:      %d to %d
                           Sell for: %d
                           ```
                           """.formatted(stats.sales() / 2, stats.sales(), stats.avgSales() != 0 ? Math.min(stats.avgSales(), stats.minPrice()) : stats.minPrice());
        builder.addField("Recommendation:", comm, false);
        for (var entry : offers.entrySet()) {
            var table = TextFormatting.getTableBuilder(entry.getValue().listings(), "Price", "Amount", "Total", "Factor", "Profit");
            for (var item : entry.getValue().listings()) {
                Price price = item.price();
                table.setNextRow(
                        full(price.pricePerUnit()),
                        full(price.quantity()),
                        full(price.total()),
                        numeric(item.factor(), 2),
                        full(item.profit()));
            }
            String offer = """
                               %s
                               Last update: %s
                               """.formatted(table.toString(), TimeFormat.RELATIVE.format(entry.getValue().itemStat()
                                                                                               .updated().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
            builder.addField(entry.getKey().name(), offer, false);
        }
        builder.setFooter("Last updated").setTimestamp(this.stats.updated());
        return builder.build();
    }

    private String numeric(double d, int decimals) {
        return ("%,." + decimals + "f").formatted(d);
    }

    private String full(int d) {
        return ("%,d").formatted(d);
    }
}
