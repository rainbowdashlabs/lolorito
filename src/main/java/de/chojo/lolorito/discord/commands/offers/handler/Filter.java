package de.chojo.lolorito.discord.commands.offers.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.util.Completion;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lolorito.core.Data;
import de.chojo.lolorito.dao.OfferFilter;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Arrays;
import java.util.List;

public class Filter implements SlashHandler {
    private final Data data;

    public Filter(Data data) {
        this.data = data;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        OfferFilter filter = data.users().user(event.getUser()).offerFilter();
        OptionMapping option = event.getOption("world");

        event.deferReply(true).complete();

        if (option != null) {
            filter.world(Worlds.worldByName(option.getAsString()));
        }

        option = event.getOption("min_unit_price");

        if (option != null) {
            filter.unitPrice(option.getAsInt());
        }

        option = event.getOption("min_profit_perc");

        if (option != null) {
            filter.factor(option.getAsInt());
        }

        option = event.getOption("min_profit");

        if (option != null) {
            filter.profit(option.getAsInt());
        }

        option = event.getOption("min_popularity");

        if (option != null) {
            filter.popularity(option.getAsDouble());
        }

        option = event.getOption("min_refresh_hours");

        if (option != null) {
            filter.refreshHours(option.getAsInt());
        }

        option = event.getOption("min_market_volume");

        if (option != null) {
            filter.marketVolume(option.getAsDouble());
        }

        option = event.getOption("min_interest");

        if (option != null) {
            filter.interest(option.getAsDouble());
        }

        option = event.getOption("min_sales");

        if (option != null) {
            filter.sales(option.getAsInt());
        }

        option = event.getOption("min_views");

        if (option != null) {
            filter.views(option.getAsInt());
        }

        option = event.getOption("limit");

        if (option != null) {
            filter.limit(option.getAsInt());
        }

        option = event.getOption("target");

        if (option != null) {
            filter.target(OfferFilter.Target.valueOf(option.getAsString()));
        }

        event.getHook().editOriginalEmbeds(filter.embed()).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery option = event.getFocusedOption();
        String name = option.getName();
        if (name.equalsIgnoreCase("world")) {
            List<Command.Choice> complete = Completion.complete(option.getValue(), Worlds.europe()
                                                                                         .worlds(), World::name);
            event.replyChoices(complete).queue();
            return;
        }
        if (name.equalsIgnoreCase("target")) {
            List<Command.Choice> complete = Completion.complete(option.getValue(), Arrays.asList(OfferFilter.Target.values()), OfferFilter.Target::name);
            event.replyChoices(complete).queue();
            return;
        }
    }
}
