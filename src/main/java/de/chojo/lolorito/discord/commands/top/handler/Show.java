package de.chojo.lolorito.discord.commands.top.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.pagination.bag.ListPageBag;
import de.chojo.jdautil.util.Completion;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lolorito.core.Data;
import de.chojo.lolorito.dao.BotUser;
import de.chojo.lolorito.dao.SearchScope;
import de.chojo.lolorito.dao.SortOrder;
import de.chojo.lolorito.dao.TopFilter;
import de.chojo.lolorito.dao.wrapper.ItemStat;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Show implements SlashHandler {
    private final Data data;

    public Show(Data data) {
        this.data = data;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var scope = event.getOption("scope", SearchScope.WORLD, s -> SearchScope.valueOf(s.getAsString()));
        var order = event.getOption("order", SortOrder.MARKET_VOLUME, s -> SortOrder.valueOf(s.getAsString()));
        var hq = event.getOption("hq", null, OptionMapping::getAsBoolean);
        var minSales = event.getOption("min_sales", null, OptionMapping::getAsInt);
        var minPopularity = event.getOption("min_popularity", null, OptionMapping::getAsDouble);
        var minMarketVolume = event.getOption("min_market_volume", null, OptionMapping::getAsDouble);
        var minInterest = event.getOption("min_interest", null, OptionMapping::getAsDouble);
        var minPrice = event.getOption("min_price", null, OptionMapping::getAsDouble);
        var minAvgPrice = event.getOption("min_avg_price", null, OptionMapping::getAsDouble);

        TopFilter topFilter = new TopFilter(scope, order, hq, minSales, minPopularity, minInterest, minMarketVolume, minPrice, minAvgPrice);
        BotUser user = data.users().user(event.getUser());
        List<ItemStat> itemStats = data.items().topItems(user, topFilter);
        context.registerPage(new ListPageBag<>(itemStats) {
            @Override
            public CompletableFuture<MessageEmbed> buildPage() {
                EmbedBuilder builder = new EmbedBuilder();
                if (currentElement().hq()) {
                    builder.setAuthor("HQ", null, "https://cdn.discordapp.com/emojis/1043942491512131634.png");
                }
                builder.setTitle(currentElement().item().name().english(), currentElement().universalisUrl());
                builder.setDescription(currentElement().prettyText());
                return CompletableFuture.completedFuture(builder.build());
            }
        });
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery option = event.getFocusedOption();
        String name = option.getName();
        if (name.equalsIgnoreCase("order")) {
            List<Command.Choice> complete = Completion.complete(option.getValue(), Arrays.asList(SortOrder.values()), SortOrder::name);
            event.replyChoices(complete).queue();
            return;
        }
        if (name.equalsIgnoreCase("scope")) {
            List<Command.Choice> complete = Completion.complete(option.getValue(), Arrays.asList(SearchScope.values()), SearchScope::name);
            event.replyChoices(complete).queue();
            return;
        }
    }
}
