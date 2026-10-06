/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.discord.util;

import de.chojo.lolorito.entity.OfferFilterRow;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * Renders an {@link OfferFilterRow} as the Discord embed the /filter command replies with.
 */
public final class OfferFilterEmbed {

    private OfferFilterEmbed() {}

    public static MessageEmbed of(OfferFilterRow filter) {
        World world = filter.worldId() > 0 ? Worlds.worldById(filter.worldId()) : null;
        String descr = """
                ```
                World:            %s
                Limit:            %d
                Unit Price:       %d
                Factor:           %.2f
                Profit:           %d
                Effective Profit: %d
                Freshness:        %d hours
                Popularity:       %.2f
                Market Volume:    %.2f
                Interest:         %.2f
                Sales:            %d
                Views:            %d
                Target:           %s
                ```
                """.formatted(
                world == null ? "(unset)" : world.name(),
                filter.offerLimit(),
                filter.unitPrice(),
                filter.factor(),
                filter.profit(),
                filter.effectiveProfit(),
                filter.refreshHours(),
                filter.popularity(),
                filter.marketVolume(),
                filter.interest(),
                filter.sales(),
                filter.views(),
                filter.target()).stripIndent();
        return new EmbedBuilder().setTitle("Filter").setDescription(descr).build();
    }
}
