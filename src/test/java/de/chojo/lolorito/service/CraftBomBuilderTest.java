/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.repository.Recipes;
import de.chojo.lolorito.value.CraftChainPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftBomBuilderTest {

    private static Recipes.Ingredient ing(int itemId, int qty, Recipes.Recipe sub) {
        return new Recipes.Ingredient(itemId, qty, sub);
    }

    private static Recipes.Recipe recipe(int id, int product, int yield, List<Recipes.Ingredient> ings) {
        return new Recipes.Recipe(id, product, "ALC", 50, yield, ings);
    }

    private static ItemDetail.PriceLevel level(int price, int qty, int world) {
        return new ItemDetail.PriceLevel(price, qty, world);
    }

    private static String name(int id) {
        return "item-" + id;
    }

    private static String world(int id) {
        return "world-" + id;
    }

    @Test
    void buyOnlyRecipeYieldsOneMaterialPerWorldSegment() {
        var r = recipe(1, 100, 1, List.of(ing(10, 5, null)));
        var chain = CraftChainPlanner.plan(r, id -> 40, 3);
        // Need 5: three fill on world 66 at 30, the rest on world 402 at 50.
        var book = Map.of(10, List.of(level(30, 3, 66), level(50, 10, 402)));

        var bom = CraftBomBuilder.build(r, chain, book, CraftBomBuilderTest::name, CraftBomBuilderTest::world);

        assertEquals(2, bom.materials().size());
        var first = bom.materials().getFirst();
        assertEquals(66, first.worldId());
        assertEquals("world-66", first.worldName());
        assertEquals(3, first.qty());
        assertEquals(90L, first.totalCost());
        var second = bom.materials().get(1);
        assertEquals(402, second.worldId());
        assertEquals(2, second.qty());
        assertEquals(100L, second.totalCost());
        assertEquals(190L, bom.materialsCost());
        assertTrue(bom.intermediates().isEmpty());
    }

    @Test
    void craftedIngredientExpandsIntoItsInputsAndAStep() {
        // Head needs 3 of item 10; item 10 is craftable (yield 2) from 2× item 20.
        var sub = recipe(2, 10, 2, List.of(ing(20, 2, null)));
        var r = recipe(1, 100, 1, List.of(ing(10, 3, sub)));
        // Buying item 10 costs 500, crafting it costs 2×50/2 = 50 → CRAFT.
        Map<Integer, Integer> prices = Map.of(10, 500, 20, 50);
        var chain = CraftChainPlanner.plan(r, prices::get, 3);
        var book = Map.of(
                10, List.of(level(500, 99, 66)),
                20, List.of(level(50, 99, 66)));

        var bom = CraftBomBuilder.build(r, chain, book, CraftBomBuilderTest::name, CraftBomBuilderTest::world);

        // 3 units needed at yield 2 → 2 runs → 4 units of item 20 bought.
        assertEquals(1, bom.materials().size());
        assertEquals(20, bom.materials().getFirst().itemId());
        assertEquals(4, bom.materials().getFirst().qty());
        assertEquals(200L, bom.materialsCost());
        assertEquals(1, bom.intermediates().size());
        var step = bom.intermediates().getFirst();
        assertEquals(10, step.itemId());
        assertEquals(4, step.qty()); // 2 runs × yield 2
        assertEquals("ALC", step.craftClass());
    }

    @Test
    void needBeyondBookDepthIsPricedAtEscalatedDeepestLevel() {
        var r = recipe(1, 100, 1, List.of(ing(10, 10, null)));
        var chain = CraftChainPlanner.plan(r, id -> 30, 3);
        var book = Map.of(10, List.of(level(30, 4, 66)));

        var bom = CraftBomBuilder.build(r, chain, book, CraftBomBuilderTest::name, CraftBomBuilderTest::world);

        assertEquals(1, bom.materials().size());
        assertEquals(10, bom.materials().getFirst().qty());
        // 4 covered at 30, 6 past book depth at ceil(30 × 1.25) = 38 —
        // the unseen tail of a book only gets more expensive.
        assertEquals(4 * 30L + 6 * 38L, bom.materialsCost());
    }

    @Test
    void sharedIngredientAcrossBranchesAggregatesBeforeAllocation() {
        // Both head ingredients resolve to buying item 20 — needs must merge
        // so the book is walked once for the combined quantity.
        var r = recipe(1, 100, 1, List.of(ing(20, 2, null), ing(20, 3, null)));
        var chain = CraftChainPlanner.plan(r, id -> 10, 3);
        var book = Map.of(20, List.of(level(10, 3, 66), level(20, 99, 402)));

        var bom = CraftBomBuilder.build(r, chain, book, CraftBomBuilderTest::name, CraftBomBuilderTest::world);

        assertEquals(2, bom.materials().size());
        assertEquals(3, bom.materials().getFirst().qty());
        assertEquals(2, bom.materials().get(1).qty());
        assertEquals(3 * 10L + 2 * 20L, bom.materialsCost());
    }
}
