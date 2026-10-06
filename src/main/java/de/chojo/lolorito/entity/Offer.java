/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import de.chojo.jdautil.text.TextFormatting;
import de.chojo.universalis.entities.Price;
import de.chojo.universalis.worlds.World;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.TimeFormat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * One arbitrage opportunity assembled from the offer explorer: home-world
 * stats plus per-source-world listings. Renders as a Discord embed on
 * demand.
 */
public record Offer(ItemStat stats, Map<World, WorldOffers> offers) {

    private static String fmt(int d) {
        return "%,d".formatted(d);
    }

    public MessageEmbed embed() {
        EmbedBuilder builder = new EmbedBuilder();
        if (stats.hq()) {
            builder.setAuthor("HQ", null, "https://cdn.discordapp.com/emojis/1043942491512131634.png");
        }
        builder.setTitle(stats.item().name().english(), stats.universalisUrl());
        builder.setDescription(statsBlock());
        var saleCount = Math.min(stats.sales() / 7, listingVolume());
        var minPrice = stats.avgSales() != 0 ? Math.min(stats.avgSales(), stats.minPrice()) : stats.minPrice();
        String comm = """
                ```
                Buy:                  %s
                Sell for:             %s
                Min effective profit: %s
                Max effective profit: %s
                ```
                """.formatted(
                        fmt(saleCount),
                        fmt(minPrice),
                        fmt(minPrice * saleCount - saleCount * maxListingPrice()),
                        fmt(minPrice * saleCount - saleCount * minListingPrice()));
        builder.addField("Recommendation:", comm, false);
        for (var entry : offers.entrySet()) {
            var table = TextFormatting.getTableBuilder(
                    entry.getValue().listings(), "Price", "Amount", "Total", "Factor", "Profit");
            for (var item : entry.getValue().listings()) {
                Price price = item.price();
                table.setNextRow(
                        fmt(price.pricePerUnit()),
                        fmt(price.quantity()),
                        fmt(price.total()),
                        "%,.2f".formatted(item.factor()),
                        fmt(item.profit()));
            }
            var joiner = new StringJoiner("\n");
            List<String> lines = Arrays.stream(table.toString().split("\n")).toList();
            for (String line : lines) {
                if (joiner.length() + line.length() > MessageEmbed.VALUE_MAX_LENGTH) break;
                joiner.add(line);
            }
            String offer = """
                    %s
                    Last update: %s
                    """.formatted(
                            joiner.toString(),
                            TimeFormat.RELATIVE.format(
                                    entry.getValue().itemStat().updated().toEpochMilli()));
            builder.addField(entry.getKey().name(), offer, false);
        }
        builder.setFooter("Last updated").setTimestamp(this.stats.updated());
        return builder.build();
    }

    private String statsBlock() {
        return """
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
                """.formatted(
                        stats.hq(),
                        stats.marketVolume(),
                        stats.interest(),
                        stats.popularity(),
                        stats.sales(),
                        stats.views(),
                        stats.minPrice(),
                        stats.avgPrice(),
                        stats.minSales(),
                        stats.avgSales(),
                        stats.maxSales(),
                        stats.listings());
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
}
