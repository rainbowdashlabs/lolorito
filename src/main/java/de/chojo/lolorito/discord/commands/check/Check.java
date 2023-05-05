package de.chojo.lolorito.discord.commands.check;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lolorito.core.Data;
import de.chojo.lolorito.discord.commands.check.handler.Show;
import de.chojo.lolorito.discord.util.ItemNameParser;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;

public class Check implements SlashProvider<Slash> {
    private final ItemNameParser itemNameParser;
    private final Data data;

    public Check(Data data) {
        itemNameParser = ItemNameParser.create(data.itemNameSupplier());
        this.data = data;
    }

    @Override
    public Slash slash() {
        return Slash.of("check", "Check the price of an item")
                .unlocalized()
                .subCommand(SubCommand.of("de", "Check with german name")
                        .handler(new Show(itemNameParser, data, Language.GERMAN))
                        .argument(Argument.text("name", "Item name").asRequired().withAutoComplete())
                        .argument(Argument.bool("hq", "high quality"))
                )
                .subCommand(SubCommand.of("en", "Check with english name")
                        .handler(new Show(itemNameParser, data, Language.ENGLISH))
                        .argument(Argument.text("name", "Item name").asRequired().withAutoComplete())
                        .argument(Argument.bool("hq", "high quality"))
                )
                .subCommand(SubCommand.of("fr", "Check with french name")
                        .handler(new Show(itemNameParser, data, Language.FRENCH))
                        .argument(Argument.text("name", "Item name").asRequired().withAutoComplete())
                        .argument(Argument.bool("hq", "high quality"))
                )
                .subCommand(SubCommand.of("jp", "Check with japanese name")
                        .handler(new Show(itemNameParser, data, Language.JAPANESE))
                        .argument(Argument.text("name", "Item name").asRequired().withAutoComplete())
                        .argument(Argument.bool("hq", "high quality")))
                .build();
    }
}
