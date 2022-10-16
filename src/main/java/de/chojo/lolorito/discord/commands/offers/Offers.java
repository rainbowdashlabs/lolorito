package de.chojo.lolorito.discord.commands.offers;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.lolorito.core.Data;
import de.chojo.lolorito.discord.commands.offers.handler.Filter;
import de.chojo.lolorito.discord.commands.offers.handler.Get;

public class Offers extends SlashCommand {
    public Offers(Data data) {
        super(Slash.of("offers", "The best offers for your world")
                .unlocalized()
                .adminCommand()
                .subCommand(SubCommand.of("filter", "Configure the filter")
                        .handler(new Filter(data))
                        .argument(Argument.text("world", "Your home world")
                                          .withAutoComplete())
                        .argument(Argument.integer("limit", "The amount of offers to retrieve")
                                          .min(1).max(10000))
                        .argument(Argument.integer("min_unit_price", "Min price of a single unit")
                                          .min(1))
                        .argument(Argument.integer("min_profit_perc", "The min profit percentage")
                                          .min(5))
                        .argument(Argument.integer("min_profit", "The min profit")
                                          .min(5))
                        .argument(Argument.integer("min_popularity", "The min popularity")
                                          .min(5))
                        .argument(Argument.integer("min_refresh_hours", "The max age of the listing")
                                          .min(1).max(120))
                        .argument(Argument.integer("min_market_volume", "The min volume of the item in the market on your world")
                                          .min(0).max(100))
                        .argument(Argument.integer("min_interest", "The minimum interest into the item on your world")
                                          .min(0).max(100))
                        .argument(Argument.integer("min_sales", "The min amount of sales in the last 7 days")
                                          .min(1))
                        .argument(Argument.integer("min_views", "The min amount of views in the last 7 days")
                                          .min(1))
                        .argument(Argument.text("target", "Search on your data center or region")
                                          .withAutoComplete())
                ).subCommand(SubCommand.of("get", "Get the best offers")
                        .handler(new Get(data)))
        );
    }
}
