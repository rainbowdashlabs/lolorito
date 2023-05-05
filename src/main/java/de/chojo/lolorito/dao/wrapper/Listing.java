package de.chojo.lolorito.dao.wrapper;

import de.chojo.jdautil.text.TextFormatting;
import de.chojo.lolorito.dao.Items.WorldListings;
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

public record Listing(ItemStat stats, Map<World, WorldListings> offers) {
    public MessageEmbed embed() {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(stats.item().name().english(), stats.universalisUrl());
        for (var entry : offers.entrySet()) {
            var table = TextFormatting.getTableBuilder(entry.getValue()
                    .listings(), "Price", "Amount", "Total");
            for (var item : entry.getValue().listings()) {
                Price price = item.price();
                table.setNextRow(
                        full(price.pricePerUnit()),
                        full(price.quantity()),
                        full(price.total()));
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
                    """.formatted(joiner.toString(), TimeFormat.RELATIVE.format(entry.getValue().listings().get(0)
                    .updated()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()));
            builder.addField(entry.getKey().name(), offer, false);
        }
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
