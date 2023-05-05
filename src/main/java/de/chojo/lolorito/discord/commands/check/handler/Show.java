package de.chojo.lolorito.discord.commands.check.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.util.Choice;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lolorito.core.Data;
import de.chojo.lolorito.dao.BotUser;
import de.chojo.lolorito.dao.wrapper.Listing;
import de.chojo.lolorito.discord.util.ItemNameParser;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Show implements SlashHandler {
    private final NameSupplier nameSupplier;
    private final ItemNameParser itemNameParser;
    private final Data data;
    private final Language language;

    public Show(ItemNameParser itemNameParser, Data data, Language language) {
        this.nameSupplier = data.itemNameSupplier();
        this.itemNameParser = itemNameParser;
        this.data = data;
        this.language = language;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        Optional<Integer> id = nameSupplier.fromName(language, event.getOption("name").getAsString());
        if (id.isEmpty()) {
            event.reply("Invalid item name").setEphemeral(true).queue();
            return;
        }

        BotUser user = data.users().user(event.getUser());
        Listing offers = data.items().offers(user, id.get(), event.getOption("hq", null, OptionMapping::getAsBoolean));
        event.replyEmbeds(offers.embed()).setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        event.replyChoices(itemNameParser.complete(language, event.getFocusedOption().getValue()).stream()
                .map(Choice::toChoice).toList()).queue();
    }
}
