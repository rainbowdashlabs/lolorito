/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.repository.MarketModels;
import de.chojo.lolorito.repository.Recipes;
import de.chojo.universalis.provider.NameSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoppingServiceIntegrationTest extends ServiceIntegrationTestBase {

    // Item 100 has an HQ variant in the real catalog; 2 (a shard) does not.
    private static final int HQABLE = 100;

    private ShoppingService service;

    @BeforeEach
    void setUp() {
        service = new ShoppingService(
                new de.chojo.lolorito.config.file.File(),
                new Recipes(dataSource),
                new ItemDetail(),
                new ItemCatalog(),
                NameSupplier.EMPTY,
                new MarketModels(dataSource));
        query("DELETE FROM listings").single(call()).delete();
        query("DELETE FROM listings_updated").single(call()).delete();
        query("DELETE FROM market_model").single(call()).delete();
        query("DELETE FROM recipe_ingredient").single(call()).delete();
        query("DELETE FROM recipe").single(call()).delete();
        insertWorld(66, "Odin", 7, "Light", "Europe");
        insertWorld(402, "Alpha", 7, "Light", "Europe");
    }

    private static void seedListing(int worldId, int itemId, int unitPrice, int qty, boolean hq) {
        query("""
                INSERT INTO listings(world, item, hq, review_time, unit_price, quantity, total)
                VALUES (:w, :i, :hq, now(), :u, :q, :t)
                """)
                .single(call().bind("w", worldId)
                        .bind("i", itemId)
                        .bind("hq", hq)
                        .bind("u", unitPrice)
                        .bind("q", qty)
                        .bind("t", unitPrice * qty))
                .insert();
        query("""
                INSERT INTO listings_updated(world, item, updated) VALUES (:w, :i, :u)
                ON CONFLICT(world, item) DO UPDATE SET updated = excluded.updated
                """)
                .single(call().bind("w", worldId)
                        .bind("i", itemId)
                        .bind(
                                "u",
                                Instant.now(),
                                de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP))
                .insert();
    }

    private void seedRecipes() {
        // Product 200 = 2× item 10 (BSM 80, yield 1); item 10 itself is
        // craftable: 3× item 20 (BSM 50, yield 1).
        query("INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty) VALUES (1, 200, 'BSM', 80, 1)")
                .single(call())
                .insert();
        query("INSERT INTO recipe_ingredient (recipe_id, item_id, quantity) VALUES (1, 10, 2)")
                .single(call())
                .insert();
        query("INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty) VALUES (2, 10, 'BSM', 50, 1)")
                .single(call())
                .insert();
        query("INSERT INTO recipe_ingredient (recipe_id, item_id, quantity) VALUES (2, 20, 3)")
                .single(call())
                .insert();
    }

    @Test
    void defaultPlanCraftsIntermediateWhenCheaper() {
        seedRecipes();
        seedListing(402, 10, 500, 10, false); // buying the intermediate: 500 apiece
        seedListing(66, 20, 50, 99, false); // crafting it: 3 × 50 = 150 apiece

        var plan = service.plan(66, 200, null, 1, Set.of(), Map.of()).orElseThrow();

        // Craft wins → shopping list holds the raw material, and item 10
        // appears as a pre-craft step (2 needed, yield 1 → 2 crafted).
        assertEquals(1, plan.preCrafts().size());
        assertEquals(10, plan.preCrafts().getFirst().itemId());
        assertEquals(2, plan.preCrafts().getFirst().qty());
        assertEquals(1, plan.stops().size());
        assertEquals(66, plan.stops().getFirst().worldId());
        var line = plan.stops().getFirst().lines().getFirst();
        assertEquals(20, line.itemId());
        assertEquals(6, line.qty());
        assertEquals(300L, plan.totalCost());
        // The tree exposes both decisions for the toggle UI.
        assertEquals("craft", plan.tree().getFirst().decision());
        assertTrue(plan.tree().getFirst().craftable());
    }

    @Test
    void buyOverrideMovesIntermediateOntoTheShoppingList() {
        seedRecipes();
        seedListing(402, 10, 500, 10, false);
        seedListing(66, 20, 50, 99, false);

        var plan = service.plan(66, 200, null, 1, Set.of(), Map.of(10, ShoppingService.Decision.BUY))
                .orElseThrow();

        assertTrue(plan.preCrafts().isEmpty());
        assertEquals(1, plan.stops().size());
        assertEquals(402, plan.stops().getFirst().worldId());
        assertEquals(10, plan.stops().getFirst().lines().getFirst().itemId());
        assertEquals(2, plan.stops().getFirst().lines().getFirst().qty());
        assertEquals(1000L, plan.totalCost());
        assertEquals("buy", plan.tree().getFirst().decision());
    }

    @Test
    void hqMarkBuysFromTheHqBoard() {
        // Product 300 = 1× HQABLE (which the real catalog says can be HQ).
        query("INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty) VALUES (3, 300, 'CRP', 10, 1)")
                .single(call())
                .insert();
        query("INSERT INTO recipe_ingredient (recipe_id, item_id, quantity) VALUES (3, :i, 1)")
                .single(call().bind("i", HQABLE))
                .insert();
        seedListing(66, HQABLE, 100, 99, false);
        seedListing(66, HQABLE, 140, 99, true);

        var unmarked = service.plan(66, 300, null, 1, Set.of(), Map.of()).orElseThrow();
        assertEquals(100L, unmarked.totalCost());

        var marked = service.plan(66, 300, null, 1, Set.of(HQABLE), Map.of()).orElseThrow();
        var line = marked.stops().getFirst().lines().getFirst();
        assertTrue(line.hq());
        assertTrue(line.canBeHq());
        assertEquals(140, line.unitPrice());
        assertEquals(140L, marked.totalCost());
    }

    @Test
    void countScalesThroughYieldAndRuns() {
        // Product 400: yield 2, needs 3× item 20 per run. Wanting 5 → 3 runs → 9 materials.
        query("INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty) VALUES (4, 400, 'ALC', 30, 2)")
                .single(call())
                .insert();
        query("INSERT INTO recipe_ingredient (recipe_id, item_id, quantity) VALUES (4, 20, 3)")
                .single(call())
                .insert();
        seedListing(66, 20, 50, 99, false);

        var plan = service.plan(66, 400, null, 5, Set.of(), Map.of()).orElseThrow();
        assertEquals(3, plan.runs());
        assertEquals(2, plan.yield());
        assertEquals(9, plan.stops().getFirst().lines().getFirst().qty());
        assertEquals(450L, plan.totalCost());
    }

    @Test
    void planListsEveryRecipeAndHonoursTheChosenOne() {
        query("INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty) VALUES (5, 500, 'GSM', 40, 1)")
                .single(call())
                .insert();
        query("INSERT INTO recipe_ingredient (recipe_id, item_id, quantity) VALUES (5, 20, 1)")
                .single(call())
                .insert();
        query("INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty) VALUES (6, 500, 'ARM', 60, 3)")
                .single(call())
                .insert();
        query("INSERT INTO recipe_ingredient (recipe_id, item_id, quantity) VALUES (6, 20, 2)")
                .single(call())
                .insert();
        seedListing(66, 20, 50, 99, false);

        var chosen = service.plan(66, 500, 6, 3, Set.of(), Map.of()).orElseThrow();

        assertEquals(2, chosen.recipes().size());
        assertEquals(
                Set.of(5, 6),
                Set.of(
                        chosen.recipes().get(0).recipeId(),
                        chosen.recipes().get(1).recipeId()));
        assertEquals(6, chosen.recipeId());
        assertEquals("ARM", chosen.craftClass());
        assertEquals(1, chosen.runs());
        assertEquals(100L, chosen.totalCost());
    }
}
