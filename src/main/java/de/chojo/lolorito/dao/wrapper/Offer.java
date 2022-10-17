package de.chojo.lolorito.dao.wrapper;

import de.chojo.jdautil.text.TextFormatting;
import de.chojo.universalis.entities.Price;
import de.chojo.universalis.worlds.World;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;
import java.util.Map;

public record Offer(ItemStat stats, Map<World, List<ItemListing>> offers) {
    public MessageEmbed embed() {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(stats.item().name().english());
        String stats = """
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
                       """.formatted(this.stats.hq(), this.stats.marketVolume(), this.stats.interest(), this.stats.popularity(),
                this.stats.sales(), this.stats.views(), this.stats.minPrice(), this.stats.avgPrice(), this.stats.minSales(),
                this.stats.avgSales(), this.stats.maxSales(), this.stats.listings());
        builder.setDescription(stats);
        for (var entry : offers.entrySet()) {
            var table = TextFormatting.getTableBuilder(entry.getValue(), "Price", "Amount", "Total", "Factor", "Profit");
            for (ItemListing item : entry.getValue()) {
                Price price = item.price();
                table.setNextRow(
                        full(price.pricePerUnit()),
                        full(price.quantity()),
                        full(price.total()),
                        numeric(item.factor(), 2),
                        full(item.profit()));
            }
            builder.addField(entry.getKey().name(), table.toString(), false);
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
