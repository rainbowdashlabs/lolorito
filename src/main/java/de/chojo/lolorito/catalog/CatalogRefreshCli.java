/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.catalog;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Build-time entrypoint invoked by the Gradle {@code refreshCatalog}
 * task. Fetches the item, recipe, and desynth data from XIVAPI +
 * Teamcraft and writes the five bundled JSON seeds under the supplied
 * output root (default: {@code src/main/resources}).
 *
 * <p>Best-effort — every fetch failure logs and returns exit code 0 so
 * a network hiccup in CI doesn't fail the build. The Gradle task wraps
 * this with {@code isIgnoreExitValue = true} as a belt-and-braces.
 */
public final class CatalogRefreshCli {

    private static final Logger log = getLogger(CatalogRefreshCli.class);

    private CatalogRefreshCli() {}

    public static void main(String[] args) {
        Path outputRoot = Path.of(args.length > 0 ? args[0] : "src/main/resources");
        log.info("CatalogRefreshCli writing to {}", outputRoot.toAbsolutePath());

        var xivapi = new XivapiClient();
        var writer = new CatalogFileWriter(outputRoot);

        try {
            var icons = new IconCatalogBuilder(xivapi).build();
            if (icons.isEmpty()) log.warn("Icon builder produced 0 rows; leaving existing files");
            else writer.writeIcons(icons);
        } catch (IOException e) {
            log.warn("Icon catalog write failed: {}", e.getMessage());
        }

        List<RecipeRecord> recipes;
        try {
            recipes = new RecipeCatalogBuilder(xivapi).build();
            if (recipes.isEmpty()) log.warn("Recipe builder produced 0 rows; leaving existing files");
            else writer.writeRecipes(recipes);
        } catch (IOException e) {
            log.warn("Recipe catalog write failed: {}", e.getMessage());
            recipes = List.of();
        }

        if (recipes.isEmpty()) {
            log.warn("Recipes empty — skipping desynth build (would produce a broken seed)");
            return;
        }

        try {
            var desynth = new DesynthCatalogBuilder(xivapi).build(recipes);
            if (desynth.isEmpty()) log.warn("Desynth builder produced 0 rows; leaving existing file");
            else
                writer.writeDesynth(
                        desynth,
                        List.of(
                                "Recipe-derived from beta.xivapi.com Recipe sheet",
                                "Teamcraft ffxiv-teamcraft/staging/libs/data/desynth.json",
                                "XIVAPI Item.ClassJobRepair + LevelItem backfill"));
        } catch (IOException e) {
            log.warn("Desynth catalog write failed: {}", e.getMessage());
        }
    }
}
