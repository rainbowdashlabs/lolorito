/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.discord.commands.offers.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.pagination.bag.ListPageBag;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lolorito.entity.Offer;
import de.chojo.lolorito.service.ItemsService;
import de.chojo.lolorito.service.UserService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Get implements SlashHandler {
    private final UserService users;
    private final ItemsService items;

    public Get(UserService users, ItemsService items) {
        this.users = users;
        this.items = items;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        event.deferReply(true).queue();
        var user = users.of(event.getUser());
        List<Offer> offers = items.bestOffersFor(user);
        ListPageBag<Offer> bag = new ListPageBag<>(offers) {
            @Override
            public CompletableFuture<MessageEmbed> buildPage() {
                return CompletableFuture.completedFuture(currentElement().embed());
            }

            @Override
            public CompletableFuture<MessageEmbed> buildEmptyPage() {
                return CompletableFuture.completedFuture(
                        new EmbedBuilder().setTitle("No match").build());
            }
        };
        context.registerPage(bag, true);
    }
}
