package de.chojo.lolorito.discord.commands.top;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.lolorito.core.Data;
import de.chojo.lolorito.discord.commands.top.handler.Show;

public class Top extends SlashCommand {
    public Top(Data data) {
        super(Slash.of("top", "Top items")
                .unlocalized()
                .command(new Show(data))
                .argument(Argument.text("scope", "Search scope").withAutoComplete())
                .argument(Argument.text("order", "Sort order").withAutoComplete())
                .argument(Argument.bool("hq", "High quality"))
                .argument(Argument.integer("min_sales", "Minimum amount of sales"))
                .argument(Argument.number("min_popularity", "Min popularity"))
                .argument(Argument.number("min_interest", "min interest"))
                .argument(Argument.number("min_market_volume", "min market volume"))
                .argument(Argument.number("min_price", "min price"))
                .argument(Argument.number("min_avg_price", "min average price"))
        );
    }
}
