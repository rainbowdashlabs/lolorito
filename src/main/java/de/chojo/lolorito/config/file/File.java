/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.config.file;

import de.chojo.lolorito.config.file.elements.BaseSettings;
import de.chojo.lolorito.config.file.elements.Catalog;
import de.chojo.lolorito.config.file.elements.Database;
import de.chojo.lolorito.config.file.elements.DiscordOauth;
import de.chojo.lolorito.config.file.elements.Http;
import de.chojo.lolorito.config.file.elements.Links;
import de.chojo.lolorito.config.file.elements.Planner;
import de.chojo.lolorito.config.file.elements.Universalis;
import de.chojo.lolorito.config.file.elements.Value;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class File {
    private BaseSettings baseSettings = new BaseSettings();
    private Database database = new Database();
    private Links links = new Links();
    private Http http = new Http();
    private DiscordOauth discordOauth = new DiscordOauth();
    private Value value = new Value();
    private Planner planner = new Planner();
    private Universalis universalis = new Universalis();
    private Catalog catalog = new Catalog();

    public BaseSettings baseSettings() {
        return baseSettings;
    }

    public Database database() {
        return database;
    }

    public Links links() {
        return links;
    }

    public Http http() {
        return http;
    }

    public DiscordOauth discordOauth() {
        return discordOauth;
    }

    public Value value() {
        return value;
    }

    public Planner planner() {
        return planner;
    }

    public Universalis universalis() {
        return universalis;
    }

    public Catalog catalog() {
        return catalog;
    }
}
