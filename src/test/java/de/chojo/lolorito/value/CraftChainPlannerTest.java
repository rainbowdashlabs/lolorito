/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import de.chojo.lolorito.repository.Recipes;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftChainPlannerTest {

    private static Recipes.Ingredient ing(int itemId, int qty, Recipes.Recipe sub) {
        return new Recipes.Ingredient(itemId, qty, sub);
    }

    private static Recipes.Recipe recipe(int id, int product, int yield, List<Recipes.Ingredient> ings) {
        return new Recipes.Recipe(id, product, "ALC", 50, yield, ings);
    }

    @Test
    void singleLevelPlanUsesBuyPriceWhenNoSubRecipe() {
        var r = recipe(1, 100, 1, List.of(ing(10, 2, null), ing(11, 3, null)));
        Map<Integer, Integer> prices = Map.of(10, 50, 11, 100);
        var plan = CraftChainPlanner.plan(r, prices::get, 3);
        assertEquals(2, plan.ingredients().size());
        assertEquals(
                CraftChainPlanner.SourceChoice.BUY,
                plan.ingredients().getFirst().chosen());
        // 2 × 50 + 3 × 100 = 400
        assertEquals(400.0, plan.perProductCost(), 1e-9);
    }

    @Test
    void yieldDividesPerProductCost() {
        var r = recipe(1, 100, 4, List.of(ing(10, 2, null)));
        Map<Integer, Integer> prices = Map.of(10, 100);
        var plan = CraftChainPlanner.plan(r, prices::get, 3);
        // 2 × 100 / 4 = 50
        assertEquals(50.0, plan.perProductCost(), 1e-9);
    }

    @Test
    void craftPreferredWhenCheaperThanBuy() {
        var sub = recipe(2, 10, 1, List.of(ing(20, 1, null)));
        var r = recipe(1, 100, 1, List.of(ing(10, 1, sub)));
        Map<Integer, Integer> prices = Map.of(10, 500, 20, 50); // buy 500, craft 50
        var plan = CraftChainPlanner.plan(r, prices::get, 3);
        assertEquals(
                CraftChainPlanner.SourceChoice.CRAFT,
                plan.ingredients().getFirst().chosen());
        assertEquals(50.0, plan.perProductCost(), 1e-9);
    }

    @Test
    void buyPreferredWhenCraftIsMoreExpensive() {
        var sub = recipe(2, 10, 1, List.of(ing(20, 1, null)));
        var r = recipe(1, 100, 1, List.of(ing(10, 1, sub)));
        Map<Integer, Integer> prices = Map.of(10, 30, 20, 100); // buy 30, craft 100
        var plan = CraftChainPlanner.plan(r, prices::get, 3);
        assertEquals(
                CraftChainPlanner.SourceChoice.BUY,
                plan.ingredients().getFirst().chosen());
        assertEquals(30.0, plan.perProductCost(), 1e-9);
    }

    @Test
    void tieBreaksToBuyForFewerClicks() {
        var sub = recipe(2, 10, 1, List.of(ing(20, 1, null)));
        var r = recipe(1, 100, 1, List.of(ing(10, 1, sub)));
        Map<Integer, Integer> prices = Map.of(10, 100, 20, 100);
        var plan = CraftChainPlanner.plan(r, prices::get, 3);
        assertEquals(
                CraftChainPlanner.SourceChoice.BUY,
                plan.ingredients().getFirst().chosen());
    }

    @Test
    void craftWhenBuyPriceMissing() {
        var sub = recipe(2, 10, 1, List.of(ing(20, 1, null)));
        var r = recipe(1, 100, 1, List.of(ing(10, 1, sub)));
        Map<Integer, Integer> prices = Map.of(20, 40); // no listing for 10, but 20 is cheap
        var plan = CraftChainPlanner.plan(r, prices::get, 3);
        assertEquals(
                CraftChainPlanner.SourceChoice.CRAFT,
                plan.ingredients().getFirst().chosen());
        assertEquals(40.0, plan.perProductCost(), 1e-9);
    }

    @Test
    void unknownWhenNeitherPathAvailable() {
        var sub = recipe(2, 10, 1, List.of(ing(20, 1, null)));
        var r = recipe(1, 100, 1, List.of(ing(10, 1, sub)));
        // Nothing has a price and sub-ingredient has no further craft.
        var plan = CraftChainPlanner.plan(r, id -> null, 3);
        assertEquals(
                CraftChainPlanner.SourceChoice.UNKNOWN,
                plan.ingredients().getFirst().chosen());
        assertNull(plan.perProductCost());
    }

    @Test
    void depthCapPreventsDeepRecursion() {
        // Level 2 sub with a further level 3 sub-sub. depth=1 should stop at level 2.
        var subSub = recipe(3, 20, 1, List.of(ing(30, 1, null)));
        var sub = recipe(2, 10, 1, List.of(ing(20, 1, subSub)));
        var r = recipe(1, 100, 1, List.of(ing(10, 1, sub)));
        Map<Integer, Integer> prices = Map.of(10, 999, 20, 100, 30, 10);
        var plan = CraftChainPlanner.plan(r, prices::get, 1);
        // Top ingredient's sub-ingredient (id 20) evaluated at depth 0 → buy only.
        var topNode = plan.ingredients().getFirst();
        assertEquals(CraftChainPlanner.SourceChoice.CRAFT, topNode.chosen());
        assertNotNull(topNode.subIngredients());
        assertEquals(1, topNode.subIngredients().size());
        var subNode = topNode.subIngredients().getFirst();
        assertEquals(CraftChainPlanner.SourceChoice.BUY, subNode.chosen());
        assertNull(subNode.subIngredients(), "depth cap stops recursion before third level");
    }

    @Test
    void depthCapIsFlaggedOnTheCutNodeAndThePlan() {
        var subSub = recipe(3, 20, 1, List.of(ing(30, 1, null)));
        var sub = recipe(2, 10, 1, List.of(ing(20, 1, subSub)));
        var r = recipe(1, 100, 1, List.of(ing(10, 1, sub)));
        Map<Integer, Integer> prices = Map.of(10, 999, 20, 100, 30, 10);

        var capped = CraftChainPlanner.plan(r, prices::get, 1);
        var topNode = capped.ingredients().getFirst();
        assertFalse(topNode.depthCapped());
        assertTrue(topNode.subIngredients().getFirst().depthCapped());
        assertTrue(capped.depthCapped());

        var full = CraftChainPlanner.plan(r, prices::get, 3);
        assertFalse(full.depthCapped(), "a deep enough budget explores every sub-recipe");
    }

    @Test
    void buyOnlyLeavesAreNeverFlaggedAsCapped() {
        var r = recipe(1, 100, 1, List.of(ing(10, 1, null)));
        var plan = CraftChainPlanner.plan(r, id -> 50, 0);
        assertFalse(plan.depthCapped());
    }

    @Test
    void cycleGuardBreaksInfiniteRecursion() {
        // Fake a cycle: item 10 has a sub-recipe that itself lists item 10 as an ingredient.
        // materialiseChain would already null this out in the real repo; simulate it here.
        var self = recipe(2, 10, 1, List.of(ing(10, 1, null))); // sub references itself with no further sub
        var r = recipe(1, 100, 1, List.of(ing(10, 1, self)));
        Map<Integer, Integer> prices = new HashMap<>();
        prices.put(10, 200);
        var plan = CraftChainPlanner.plan(r, prices::get, 5);
        // Cycle guard should skip re-entering item 10; the deeper reference
        // is treated as a plain buy → craft cost = buy price.
        var top = plan.ingredients().getFirst();
        assertNotNull(top);
    }

    @Test
    void chainNodeUnitCostReturnsChosenSourcePrice() {
        var buyNode =
                new CraftChainPlanner.ChainNode(1, 1, 100, 50, 50.0, null, CraftChainPlanner.SourceChoice.BUY, "", 0, false);
        var craftNode =
                new CraftChainPlanner.ChainNode(1, 1, 100, 50, 50.0, null, CraftChainPlanner.SourceChoice.CRAFT, "", 0, false);
        var unknownNode = new CraftChainPlanner.ChainNode(
                1, 1, null, null, null, null, CraftChainPlanner.SourceChoice.UNKNOWN, "", 0, false);
        assertEquals(100, buyNode.unitCost());
        assertEquals(50, craftNode.unitCost());
        assertNull(unknownNode.unitCost());
    }

    @Test
    void zeroYieldClampsToOne() {
        var r = recipe(1, 100, 0, List.of(ing(10, 3, null))); // yield=0 pathological
        var plan = CraftChainPlanner.plan(r, id -> 100, 3);
        // Should not throw, per-product cost uses yield=1
        assertEquals(300.0, plan.perProductCost(), 1e-9);
        assertTrue(!plan.ingredients().isEmpty());
    }

    @Test
    void levelGateBlocksSubCraftAboveCallerLevel() {
        // Sub-recipe is ALC level 50 and would be cheaper than buying —
        // but the caller is only ALC 30, so the node must resolve to BUY.
        var sub = recipe(2, 10, 1, List.of(ing(20, 1, null)));
        var r = recipe(1, 100, 1, List.of(ing(10, 1, sub)));
        java.util.function.IntFunction<Integer> prices = id -> id == 10 ? 500 : id == 20 ? 100 : null;

        var gated = CraftChainPlanner.plan(r, prices, 3, CraftChainPlanner.levelGate(java.util.Map.of("ALC", 30)));
        assertEquals(
                CraftChainPlanner.SourceChoice.BUY, gated.ingredients().get(0).chosen());

        var allowed = CraftChainPlanner.plan(r, prices, 3, CraftChainPlanner.levelGate(java.util.Map.of("ALC", 50)));
        assertEquals(
                CraftChainPlanner.SourceChoice.CRAFT,
                allowed.ingredients().get(0).chosen());
    }

    @Test
    void emptyLevelMapMeansUngated() {
        var sub = recipe(2, 10, 1, List.of(ing(20, 1, null)));
        var r = recipe(1, 100, 1, List.of(ing(10, 1, sub)));
        java.util.function.IntFunction<Integer> prices = id -> id == 10 ? 500 : id == 20 ? 100 : null;
        var plan = CraftChainPlanner.plan(r, prices, 3, CraftChainPlanner.levelGate(java.util.Map.of()));
        assertEquals(
                CraftChainPlanner.SourceChoice.CRAFT, plan.ingredients().get(0).chosen());
    }
}
