/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipesIntegrationTest extends RepositoryTestBase {

    private Recipes repo;

    private static void seedRecipe(int id, int productId, String craftClass, int level, int yield) {
        query("""
                INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty)
                VALUES (:id, :p, :c, :l, :y)
                """)
                .single(call().bind("id", id)
                        .bind("p", productId)
                        .bind("c", craftClass)
                        .bind("l", level)
                        .bind("y", yield))
                .insert();
    }

    private static void seedIngredient(int recipeId, int itemId, int qty) {
        query("""
                INSERT INTO recipe_ingredient (recipe_id, item_id, quantity)
                VALUES (:r, :i, :q)
                """)
                .single(call().bind("r", recipeId).bind("i", itemId).bind("q", qty))
                .insert();
    }

    @BeforeEach
    void setUp() {
        repo = new Recipes(dataSource);
        query("DELETE FROM recipe_ingredient").single(call()).delete();
        query("DELETE FROM recipe").single(call()).delete();
    }

    @Test
    void findByProductReturnsRecipesWithIngredients() {
        seedRecipe(1, 100, "BSM", 30, 1);
        seedIngredient(1, 10, 3);
        seedIngredient(1, 11, 2);
        seedRecipe(2, 100, "ARM", 30, 2);
        seedIngredient(2, 20, 1);

        var recipes = repo.findByProduct(100);
        assertEquals(2, recipes.size(), "both recipes producing item 100 come back");
        var byId = recipes.stream().collect(Collectors.toMap(Recipes.Recipe::id, r -> r));
        var one = byId.get(1);
        assertEquals("BSM", one.craftClass());
        assertEquals(1, one.yield());
        assertEquals(2, one.ingredients().size());
        var two = byId.get(2);
        assertEquals("ARM", two.craftClass());
        assertEquals(2, two.yield());
        assertEquals(1, two.ingredients().size());
        assertEquals(20, two.ingredients().getFirst().itemId());
    }

    @Test
    void findByProductReturnsEmptyListWhenAbsent() {
        assertTrue(repo.findByProduct(999).isEmpty());
    }

    @Test
    void findByIdReturnsSingleRecipe() {
        seedRecipe(1, 100, "GSM", 40, 3);
        seedIngredient(1, 12, 5);
        var recipe = repo.find(1).orElseThrow();
        assertEquals(1, recipe.id());
        assertEquals("GSM", recipe.craftClass());
        assertEquals(3, recipe.yield());
        assertEquals(1, recipe.ingredients().size());
    }

    @Test
    void findByIdReturnsEmptyWhenAbsent() {
        assertTrue(repo.find(42).isEmpty());
    }

    @Test
    void recipeWithoutIngredientsHasEmptyList() {
        seedRecipe(1, 100, "CUL", 10, 1);
        var recipe = repo.find(1).orElseThrow();
        assertTrue(recipe.ingredients().isEmpty());
    }

    // -- Transitive load ---------------------------------------------------

    @Test
    void ingredientCarriesItsOwnRecipeWhenOneExists() {
        // Recipe 1 produces item 100 from item 200. Item 200 has its own recipe 2 from item 300.
        // Loading recipe 1 should surface recipe 2 nested under the ingredient.
        seedRecipe(1, 100, "BSM", 30, 1);
        seedIngredient(1, 200, 1);
        seedRecipe(2, 200, "BSM", 20, 1);
        seedIngredient(2, 300, 3);

        var recipe = repo.find(1).orElseThrow();
        assertEquals(1, recipe.ingredients().size());
        var subRecipe = recipe.ingredients().getFirst().recipeToCraftIt();
        assertNotNull(subRecipe, "ingredient 200 is craftable → its recipe should be attached");
        assertEquals(2, subRecipe.id());
        assertEquals(1, subRecipe.ingredients().size());
        assertEquals(300, subRecipe.ingredients().getFirst().itemId());
        assertTrue(
                subRecipe.ingredients().getFirst().recipeToCraftIt() == null,
                "raw ingredient 300 has no recipe → null pointer");
    }

    @Test
    void findByProductAlsoResolvesTransitively() {
        seedRecipe(1, 100, "GSM", 40, 3);
        seedIngredient(1, 200, 5);
        seedRecipe(2, 200, "BSM", 20, 1);
        seedIngredient(2, 300, 1);

        var recipes = repo.findByProduct(100);
        assertEquals(1, recipes.size());
        var sub = recipes.getFirst().ingredients().getFirst().recipeToCraftIt();
        assertNotNull(sub);
        assertEquals(2, sub.id());
    }

    @Test
    void cycleGuardBreaksInfiniteRecursion() {
        // Pathological: recipe 1 crafts item 100 from item 200; recipe 2 crafts item 200 from item 100.
        // Real FFXIV recipes shouldn't do this, but the loader must survive it.
        seedRecipe(1, 100, "BSM", 30, 1);
        seedIngredient(1, 200, 1);
        seedRecipe(2, 200, "BSM", 30, 1);
        seedIngredient(2, 100, 1);

        var recipe = repo.find(1).orElseThrow();
        // Depth 1: recipe 1 → ingredient 200 (with recipe 2 attached).
        var sub = recipe.ingredients().getFirst().recipeToCraftIt();
        assertNotNull(sub);
        assertEquals(2, sub.id());
        // Depth 2: recipe 2 → ingredient 100 (visited set breaks the loop, no recipe attached).
        assertEquals(100, sub.ingredients().getFirst().itemId());
        assertTrue(
                sub.ingredients().getFirst().recipeToCraftIt() == null,
                "cycle guard: revisiting recipe 1 should return a flat ingredient list");
    }
}
