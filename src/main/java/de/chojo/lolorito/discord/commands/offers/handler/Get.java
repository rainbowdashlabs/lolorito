package de.chojo.lolorito.discord.commands.offers.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.pagination.bag.ListPageBag;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lolorito.core.Data;
import de.chojo.lolorito.dao.BotUser;
import de.chojo.lolorito.dao.wrapper.Offer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Get implements SlashHandler {
    private final Data data;

    public Get(Data data) {
        this.data = data;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        event.deferReply(true).queue();
        BotUser user = data.users().user(event.getUser());
        List<Offer> offers = data.items().bestOffers(user);
        ListPageBag<Offer> bag = new ListPageBag<>(offers) {
            @Override
            public CompletableFuture<MessageEmbed> buildPage() {
                return CompletableFuture.completedFuture(currentElement().embed());
            }

            @Override
            public CompletableFuture<MessageEmbed> buildEmptyPage() {
                return CompletableFuture.completedFuture(new EmbedBuilder().setTitle("No match").build());
            }
        };
        context.registerPage(bag, true);
    }
}
