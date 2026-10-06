/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.discord.commands.offers.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.util.Completion;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lolorito.discord.util.OfferFilterEmbed;
import de.chojo.lolorito.entity.OfferFilterTarget;
import de.chojo.lolorito.service.FilterService;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /offers filter …}. Every slash option is optional — the handler
 * hands the bundle to {@link FilterService#applyDiscordOptions} which
 * merges non-null fields into the persisted row.
 */
public class Filter implements SlashHandler {
    private final FilterService filters;

    public Filter(FilterService filters) {
        this.filters = filters;
    }

    private static World worldOpt(SlashCommandInteractionEvent e) {
        OptionMapping o = e.getOption("world");
        return o == null ? null : Worlds.worldByName(o.getAsString());
    }

    private static OfferFilterTarget targetOpt(SlashCommandInteractionEvent e) {
        OptionMapping o = e.getOption("target");
        if (o == null) return null;
        try {
            return OfferFilterTarget.valueOf(o.getAsString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Integer intOpt(SlashCommandInteractionEvent e, String name) {
        OptionMapping o = e.getOption(name);
        return o == null ? null : o.getAsInt();
    }

    private static Double doubleOpt(SlashCommandInteractionEvent e, String name) {
        OptionMapping o = e.getOption(name);
        return o == null ? null : o.getAsDouble();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        event.deferReply(true).complete();

        var options = new FilterService.DiscordOptions(
                worldOpt(event),
                intOpt(event, "limit"),
                intOpt(event, "unit_price"),
                doubleOpt(event, "factor"),
                intOpt(event, "refresh_hours"),
                doubleOpt(event, "popularity"),
                doubleOpt(event, "market_volume"),
                doubleOpt(event, "interest"),
                intOpt(event, "sales"),
                intOpt(event, "views"),
                intOpt(event, "profit"),
                intOpt(event, "effective_profit"),
                targetOpt(event));

        var row = filters.applyDiscordOptions(event.getUser().getIdLong(), options);
        event.getHook().editOriginalEmbeds(OfferFilterEmbed.of(row)).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery option = event.getFocusedOption();
        String name = option.getName();
        if (name.equalsIgnoreCase("world")) {
            var allWorlds =
                    Worlds.regions().stream().flatMap(r -> r.worlds().stream()).toList();
            List<Command.Choice> complete = Completion.complete(option.getValue(), allWorlds, World::name);
            event.replyChoices(complete).queue();
            return;
        }
        if (name.equalsIgnoreCase("target")) {
            List<Command.Choice> complete = Completion.complete(
                    option.getValue(), Arrays.asList(OfferFilterTarget.values()), OfferFilterTarget::name);
            event.replyChoices(complete).queue();
            return;
        }
    }
}
