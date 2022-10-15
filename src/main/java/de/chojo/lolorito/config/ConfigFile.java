package de.chojo.lolorito.config;

import de.chojo.lolorito.config.elements.BaseSettings;
import de.chojo.lolorito.config.elements.Database;
import de.chojo.lolorito.config.elements.Links;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class ConfigFile {
    private BaseSettings baseSettings = new BaseSettings();
    private Database database = new Database();
    private Links links = new Links();

    public BaseSettings baseSettings() {
        return baseSettings;
    }

    public Database database() {
        return database;
    }

    public Links links() {
        return links;
    }
}
