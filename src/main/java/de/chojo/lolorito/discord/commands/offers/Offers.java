/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.discord.commands.offers;

import com.google.inject.Inject;
import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.lolorito.discord.commands.offers.handler.Filter;
import de.chojo.lolorito.discord.commands.offers.handler.Get;
import de.chojo.lolorito.service.FilterService;
import de.chojo.lolorito.service.ItemsService;
import de.chojo.lolorito.service.UserService;

public class Offers extends SlashCommand {
    @Inject
    public Offers(FilterService filters, UserService users, ItemsService items) {
        super(Slash.of("offers", "The best offers for your world")
                .unlocalized()
                .adminCommand()
                .subCommand(SubCommand.of("filter", "Configure the filter")
                        .handler(new Filter(filters))
                        .argument(Argument.text("world", "Your home world").withAutoComplete())
                        .argument(Argument.integer("limit", "The amount of offers to retrieve")
                                .min(1)
                                .max(10000))
                        .argument(Argument.integer("unit_price", "Min price of a single unit")
                                .min(1))
                        .argument(Argument.number("factor", "The profit factor").min(1))
                        .argument(Argument.integer("profit", "The min profit")
                                .min(1)
                                .max(1000000))
                        .argument(Argument.integer("effective_profit", "The min effective profit")
                                .min(1)
                                .max(1000000))
                        .argument(Argument.number("popularity", "The min popularity")
                                .min(0)
                                .max(100))
                        .argument(Argument.integer("refresh_hours", "The max age of the listing")
                                .min(1)
                                .max(120))
                        .argument(Argument.number(
                                        "market_volume", "The min volume of the item in the market on your world")
                                .min(0)
                                .max(100))
                        .argument(Argument.number("interest", "The minimum interest into the item on your world")
                                .min(0)
                                .max(100))
                        .argument(Argument.integer("sales", "The min amount of sales in the last 7 days")
                                .min(0))
                        .argument(Argument.integer("views", "The min amount of views in the last 7 days")
                                .min(0))
                        .argument(Argument.text("target", "Search on your data center or region")
                                .withAutoComplete()))
                .subCommand(SubCommand.of("get", "Get the best offers").handler(new Get(users, items))));
    }
}
