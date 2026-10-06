/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.discord.commands.check.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.util.Choice;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lolorito.discord.util.ItemNameParser;
import de.chojo.lolorito.entity.Listing;
import de.chojo.lolorito.service.ItemsService;
import de.chojo.lolorito.service.UserService;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Show implements SlashHandler {
    private final ItemNameParser itemNameParser;
    private final NameSupplier nameSupplier;
    private final UserService users;
    private final ItemsService items;
    private final Language language;

    public Show(
            ItemNameParser itemNameParser,
            NameSupplier nameSupplier,
            UserService users,
            ItemsService items,
            Language language) {
        this.itemNameParser = itemNameParser;
        this.nameSupplier = nameSupplier;
        this.users = users;
        this.items = items;
        this.language = language;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        Optional<Integer> id =
                nameSupplier.fromName(language, event.getOption("name").getAsString());
        if (id.isEmpty()) {
            event.reply("Invalid item name").setEphemeral(true).queue();
            return;
        }

        var user = users.of(event.getUser());
        Listing offers = items.listingFor(user, id.get(), event.getOption("hq", null, OptionMapping::getAsBoolean));
        event.replyEmbeds(offers.embed()).setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        event.replyChoices(itemNameParser
                        .complete(language, event.getFocusedOption().getValue())
                        .stream()
                        .map(Choice::toChoice)
                        .toList())
                .queue();
    }
}
