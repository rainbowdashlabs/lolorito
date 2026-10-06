/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.catalog.DesynthSource;
import de.chojo.lolorito.catalog.RecipeRecord;
import de.chojo.lolorito.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Smoke test that the shipped {@code recipes.json} and
 * {@code desynth_results.json} actually load through
 * {@link CraftDesynthLoader#loadAll()}. Guards against schema drift
 * between the catalog builder and the loader, and against JSON payloads
 * that would silently produce zero rows (which is exactly what happened
 * to the recipe refresh when beta.xivapi renamed the ingredient fields).
 *
 * <p>Since the catalog JSONs are now regenerated at build time
 * ({@code ./gradlew refreshCatalog}) rather than committed, this test
 * skips gracefully when the classpath resources are absent — a fresh
 * clone without a prior refresh sees an assumption failure, not an
 * assertion failure. CI runs {@code refreshCatalog} in the Docker
 * build so the shipped jar always has the seed.
 */
class CraftDesynthLoaderIntegrationTest extends RepositoryTestBase {

    @Test
    void loadsShippedSeeds() {
        boolean recipesOnClasspath =
                CraftDesynthLoader.class.getClassLoader().getResource("recipe/recipes.json") != null;
        assumeTrue(
                recipesOnClasspath,
                "Catalog seed missing on classpath — run `./gradlew refreshCatalog` to populate it before running this test.");

        query("DELETE FROM recipe_ingredient").single(call()).delete();
        query("DELETE FROM recipe").single(call()).delete();
        query("DELETE FROM desynth_result").single(call()).delete();

        CraftDesynthLoader.loadAll();

        Integer recipes = query("SELECT count(*) AS c FROM recipe")
                .single(call())
                .map(r -> r.getInt("c"))
                .first()
                .orElse(0);
        Integer ingredients = query("SELECT count(*) AS c FROM recipe_ingredient")
                .single(call())
                .map(r -> r.getInt("c"))
                .first()
                .orElse(0);
        Integer desynth = query("SELECT count(*) AS c FROM desynth_result")
                .single(call())
                .map(r -> r.getInt("c"))
                .first()
                .orElse(0);

        assertNotNull(recipes);
        assertNotNull(ingredients);
        assertNotNull(desynth);

        // The refreshed seeds land in the thousands. Assert only that a
        // non-trivial number of rows survived the round-trip so we catch
        // shape drift without pinning to exact counts.
        assertTrue(recipes > 1000, "recipes loaded: " + recipes);
        assertTrue(ingredients > recipes, "ingredients (" + ingredients + ") should exceed recipes (" + recipes + ")");
        assertTrue(desynth > 1000, "desynth rows loaded: " + desynth);
    }

    @Test
    void loadFromRecordsInsertsRowsWithoutJsonRoundTrip() {
        query("DELETE FROM recipe_ingredient").single(call()).delete();
        query("DELETE FROM recipe").single(call()).delete();
        query("DELETE FROM desynth_result").single(call()).delete();

        var recipes = List.of(
                new RecipeRecord(7777, 4242, "carpenter", 30, 1, List.of(new RecipeRecord.Ingredient(4243, 3))));
        var desynth =
                List.of(new DesynthSource(4242, "leatherworker", 42, List.of(new DesynthSource.Component(4244, 0.5))));

        CraftDesynthLoader.load(recipes, desynth);

        int loadedRecipes = query("SELECT count(*) AS c FROM recipe WHERE id = 7777")
                .single(call())
                .map(r -> r.getInt("c"))
                .first()
                .orElse(0);
        int loadedIngredients = query("SELECT count(*) AS c FROM recipe_ingredient WHERE recipe_id = 7777")
                .single(call())
                .map(r -> r.getInt("c"))
                .first()
                .orElse(0);
        String desynthClass = query("SELECT desynth_class FROM desynth_result WHERE source_item_id = 4242")
                .single(call())
                .map(r -> r.getString("desynth_class"))
                .first()
                .orElse(null);
        int desynthLevel = query("SELECT desynth_level FROM desynth_result WHERE source_item_id = 4242")
                .single(call())
                .map(r -> r.getInt("desynth_level"))
                .first()
                .orElse(0);

        assertEquals(1, loadedRecipes);
        assertEquals(1, loadedIngredients);
        assertEquals("leatherworker", desynthClass);
        assertEquals(42, desynthLevel);
    }
}
