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
 * {@code /check} response — stats for the home-world key plus listings from
 * every world in the DC. Renders as a Discord embed on demand.
 */
public record Listing(ItemStat stats, Map<World, WorldListings> offers) {

    private static String fmt(int d) {
        return "%,d".formatted(d);
    }

    public MessageEmbed embed() {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(stats.item().name().english(), stats.universalisUrl());
        for (var entry : offers.entrySet()) {
            var table = TextFormatting.getTableBuilder(entry.getValue().listings(), "Price", "Amount", "Total");
            for (var item : entry.getValue().listings()) {
                Price price = item.price();
                table.setNextRow(fmt(price.pricePerUnit()), fmt(price.quantity()), fmt(price.total()));
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
                            entry.getValue().listings().getFirst().updated().toEpochMilli()));
            builder.addField(entry.getKey().name(), offer, false);
        }
        return builder.build();
    }
}
