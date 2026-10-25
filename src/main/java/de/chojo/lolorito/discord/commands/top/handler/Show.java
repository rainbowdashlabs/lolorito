/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.discord.commands.top.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.pagination.bag.ListPageBag;
import de.chojo.jdautil.util.Completion;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lolorito.entity.ItemStat;
import de.chojo.lolorito.entity.SearchScope;
import de.chojo.lolorito.entity.SortOrder;
import de.chojo.lolorito.entity.TopFilter;
import de.chojo.lolorito.service.ItemsService;
import de.chojo.lolorito.service.UserService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Show implements SlashHandler {
    private final UserService users;
    private final ItemsService items;

    public Show(UserService users, ItemsService items) {
        this.users = users;
        this.items = items;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var scope = enumOption(event, "scope", SearchScope.class, SearchScope.WORLD);
        var order = enumOption(event, "order", SortOrder.class, SortOrder.MARKET_VOLUME);
        if (scope.isEmpty()) {
            replyInvalid(event, "scope", SearchScope.values());
            return;
        }
        if (order.isEmpty()) {
            replyInvalid(event, "order", SortOrder.values());
            return;
        }
        var hq = event.getOption("hq", null, OptionMapping::getAsBoolean);
        var minSales = event.getOption("min_sales", null, OptionMapping::getAsInt);
        var minPopularity = event.getOption("min_popularity", null, OptionMapping::getAsDouble);
        var minMarketVolume = event.getOption("min_market_volume", null, OptionMapping::getAsDouble);
        var minInterest = event.getOption("min_interest", null, OptionMapping::getAsDouble);
        var minPrice = event.getOption("min_price", null, OptionMapping::getAsDouble);
        var minAvgPrice = event.getOption("min_avg_price", null, OptionMapping::getAsDouble);

        TopFilter topFilter = new TopFilter(
                scope.get(),
                order.get(),
                hq,
                minSales,
                minPopularity,
                minInterest,
                minMarketVolume,
                minPrice,
                minAvgPrice);
        var user = users.of(event.getUser());
        List<ItemStat> itemStats = items.topFor(user, topFilter);
        context.registerPage(new ListPageBag<>(itemStats) {
            @Override
            public MessageEditData buildPage() {
                EmbedBuilder builder = new EmbedBuilder();
                if (currentElement().hq()) {
                    builder.setAuthor("HQ", null, "https://cdn.discordapp.com/emojis/1043942491512131634.png");
                }
                builder.setTitle(
                        currentElement().item().name().english(),
                        currentElement().universalisUrl());
                builder.setDescription(currentElement().prettyText());
                return MessageEditData.fromEmbeds(builder.build());
            }

            @Override
            public MessageEditData buildEmptyPage() {
                return MessageEditData.fromEmbeds(new EmbedBuilder()
                        .setTitle("No items match")
                        .setDescription(
                                "Loosen the filters, or set your home world first with `/offers filter world:`.")
                        .build());
            }
        });
    }

    /**
     * Read an enum option by its constant name, ignoring case. A missing option gives the fallback; a value that names
     * no constant gives an empty result, because autocomplete only suggests values and Discord accepts any text.
     */
    private static <E extends Enum<E>> Optional<E> enumOption(
            SlashCommandInteractionEvent event, String name, Class<E> type, E fallback) {
        var option = event.getOption(name);
        if (option == null) return Optional.of(fallback);
        String value = option.getAsString().trim();
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equalsIgnoreCase(value))
                .findFirst();
    }

    private static void replyInvalid(SlashCommandInteractionEvent event, String name, Enum<?>[] allowed) {
        String values = Arrays.stream(allowed).map(e -> "`" + e.name() + "`").collect(Collectors.joining(", "));
        event.reply("Unknown `%s`. Use one of %s.".formatted(name, values))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery option = event.getFocusedOption();
        String name = option.getName();
        if (name.equalsIgnoreCase("order")) {
            List<Command.Choice> complete =
                    Completion.complete(option.getValue(), Arrays.asList(SortOrder.values()), SortOrder::name);
            event.replyChoices(complete).queue();
            return;
        }
        if (name.equalsIgnoreCase("scope")) {
            List<Command.Choice> complete =
                    Completion.complete(option.getValue(), Arrays.asList(SearchScope.values()), SearchScope::name);
            event.replyChoices(complete).queue();
            return;
        }
    }
}
