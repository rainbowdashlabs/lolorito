/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.discord.commands.check;

import com.google.inject.Inject;
import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.jdautil.interactions.slash.structure.builder.SubCommandBuilder;
import de.chojo.lolorito.discord.commands.check.handler.Show;
import de.chojo.lolorito.discord.util.ItemNameParser;
import de.chojo.lolorito.service.ItemsService;
import de.chojo.lolorito.service.UserService;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;

public class Check implements SlashProvider<Slash> {
    private final ItemNameParser itemNameParser;
    private final NameSupplier nameSupplier;
    private final UserService users;
    private final ItemsService items;

    @Inject
    public Check(NameSupplier nameSupplier, UserService users, ItemsService items) {
        this.nameSupplier = nameSupplier;
        this.itemNameParser = ItemNameParser.create(nameSupplier);
        this.users = users;
        this.items = items;
    }

    @Override
    public Slash slash() {
        return Slash.of("check", "Check the price of an item")
                .unlocalized()
                .subCommand(sub("de", "Check with german name", Language.GERMAN))
                .subCommand(sub("en", "Check with english name", Language.ENGLISH))
                .subCommand(sub("fr", "Check with french name", Language.FRENCH))
                .subCommand(sub("jp", "Check with japanese name", Language.JAPANESE))
                .build();
    }

    private SubCommandBuilder sub(String key, String description, Language language) {
        return SubCommand.of(key, description)
                .handler(new Show(itemNameParser, nameSupplier, users, items, language))
                .argument(Argument.text("name", "Item name").asRequired().withAutoComplete())
                .argument(Argument.bool("hq", "high quality"));
    }
}
