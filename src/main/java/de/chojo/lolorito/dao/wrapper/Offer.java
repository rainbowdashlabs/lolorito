package de.chojo.lolorito.dao.wrapper;

import de.chojo.jdautil.text.TextFormatting;
import de.chojo.lolorito.dao.Items.WorldOffers;
import de.chojo.universalis.entities.Price;
import de.chojo.universalis.worlds.World;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.TimeFormat;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public record Offer(ItemStat stats, Map<World, WorldOffers> offers) {
    public MessageEmbed embed() {
        EmbedBuilder builder = new EmbedBuilder();
        if (stats.hq()) {
            builder.setAuthor("HQ", null, "https://cdn.discordapp.com/emojis/1043942491512131634.png");
        }
        builder.setTitle(stats.item().name().english(), stats.universalisUrl());
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
        var saleCount = Math.min(stats.sales() / 7, listingVolume());
        var minPrice = stats.avgSales() != 0 ? Math.min(stats.avgSales(), stats.minPrice()) : stats.minPrice();
        String comm = """
                      ```
                      Buy:                  %s
                      Sell for:             %s
                      Min effective profit: %s
                      Max effective profit: %s
                      ```
                      """.formatted(full(saleCount),
                full(minPrice),
                full(minPrice * saleCount - saleCount * maxListingPrice()),
                full(minPrice * saleCount - saleCount * minListingPrice()));
        builder.addField("Recommendation:", comm, false);
        for (var entry : offers.entrySet()) {
            var table = TextFormatting.getTableBuilder(entry.getValue()
                                                            .listings(), "Price", "Amount", "Total", "Factor", "Profit");
            for (var item : entry.getValue().listings()) {
                Price price = item.price();
                table.setNextRow(
                        full(price.pricePerUnit()),
                        full(price.quantity()),
                        full(price.total()),
                        numeric(item.factor(), 2),
                        full(item.profit()));
            }
            var joiner = new StringJoiner("\n");

            List<String> lines = Arrays.stream(table.toString().split("\n")).toList();
            for (String line : lines) {
                if (joiner.length() + line.length() > MessageEmbed.VALUE_MAX_LENGTH) {
                    break;
                }
                joiner.add(line);
            }

            String offer = """
                           %s
                           Last update: %s
                           """.formatted(joiner.toString(), TimeFormat.RELATIVE.format(entry.getValue().itemStat()
                                                                                 .updated()
                                                                                 .atZone(ZoneId.systemDefault())
                                                                                 .toInstant()
                                                                                 .toEpochMilli()));
            builder.addField(entry.getKey().name(), offer, false);
        }
        builder.setFooter("Last updated").setTimestamp(this.stats.updated());
        return builder.build();
    }

    private int minListingPrice() {
        return offers.values().stream()
                     .flatMap(list -> list.listings().stream())
                     .mapToInt(listing -> listing.price().pricePerUnit())
                     .min()
                     .orElse(0);
    }

    private int maxListingPrice() {
        return offers.values().stream()
                     .flatMap(list -> list.listings().stream())
                     .mapToInt(listing -> listing.price().pricePerUnit())
                     .max()
                     .orElse(0);
    }

    private int listingVolume() {
        return offers.values().stream()
                     .flatMap(list -> list.listings().stream())
                     .mapToInt(listing -> listing.price().quantity())
                     .sum();
    }

    private String numeric(double d, int decimals) {
        return ("%,." + decimals + "f").formatted(d);
    }

    private String full(int d) {
        return ("%,d").formatted(d);
    }
}
