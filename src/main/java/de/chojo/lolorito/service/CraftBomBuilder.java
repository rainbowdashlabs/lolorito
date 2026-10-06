/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.planner.PlanCraftMaterial;
import de.chojo.lolorito.planner.PlanCraftStep;
import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.repository.Recipes;
import de.chojo.lolorito.value.CraftChainPlanner;
import de.chojo.lolorito.value.ListingBook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Flattens a {@link CraftChainPlanner.ChainPlan} into a shopping list.
 * The chain says buy-vs-craft per node; this walks it alongside the
 * recipe DAG (same shape by construction) and produces, for one craft
 * run of the head recipe:
 *
 * <ul>
 *   <li><b>materials</b> — every BUY leaf, quantities multiplied through
 *       the sub-craft runs above it, allocated against the cheap side of
 *       the book so each line names the world holding the listings;</li>
 *   <li><b>intermediates</b> — every CRAFT node as a pre-craft step, in
 *       dependency order (deepest first).</li>
 * </ul>
 *
 * <p>Like {@link ListingBook#unitCostFor}, a need deeper than the fetched
 * book is priced at the deepest known level — optimistic but bounded, and
 * the caller controls the level count.
 */
final class CraftBomBuilder {

    private CraftBomBuilder() {}

    /** The flattened shopping list; {@code materialsCost} sums the material lines. */
    record Bom(List<PlanCraftMaterial> materials, List<PlanCraftStep> intermediates, long materialsCost) {}

    static Bom build(
            Recipes.Recipe recipe,
            CraftChainPlanner.ChainPlan chain,
            Map<Integer, List<ItemDetail.PriceLevel>> book,
            IntFunction<String> itemName,
            IntFunction<String> worldName) {
        return build(recipe, chain, book, itemName, worldName, new LinkedHashMap<>());
    }

    /**
     * Same walk, sharing book depth with earlier allocations: units
     * already recorded in {@code consumedByItem} are skipped before this
     * BOM takes its own — and this BOM's takes are recorded back. Lets
     * the plan assembly price several chosen crafts against ONE book
     * instead of every craft assuming it gets the cheapest listings.
     */
    static Bom build(
            Recipes.Recipe recipe,
            CraftChainPlanner.ChainPlan chain,
            Map<Integer, List<ItemDetail.PriceLevel>> book,
            IntFunction<String> itemName,
            IntFunction<String> worldName,
            Map<Integer, Integer> consumedByItem) {
        var needs = new LinkedHashMap<Integer, Integer>();
        var steps = new ArrayList<PlanCraftStep>();
        List<Recipes.Ingredient> ingredients = recipe.ingredients();
        List<CraftChainPlanner.ChainNode> nodes = chain.ingredients();
        for (int i = 0; i < ingredients.size() && i < nodes.size(); i++) {
            walk(ingredients.get(i), nodes.get(i), ingredients.get(i).quantity(), needs, steps, itemName);
        }

        var materials = new ArrayList<PlanCraftMaterial>();
        long total = 0;
        for (var need : needs.entrySet()) {
            var segments = allocate(
                    need.getKey(), need.getValue(), book.get(need.getKey()), itemName, worldName, consumedByItem);
            for (var m : segments) total += m.totalCost();
            materials.addAll(segments);
        }
        return new Bom(List.copyOf(materials), List.copyOf(steps), total);
    }

    /**
     * Accumulate buy needs and pre-craft steps for {@code needed} units of
     * one ingredient. A CRAFT node expands into its children scaled by the
     * number of sub-craft runs; anything else lands on the shopping list.
     */
    private static void walk(
            Recipes.Ingredient ing,
            CraftChainPlanner.ChainNode node,
            int needed,
            Map<Integer, Integer> needs,
            List<PlanCraftStep> steps,
            IntFunction<String> itemName) {
        Recipes.Recipe sub = ing.recipeToCraftIt();
        boolean crafted =
                node.chosen() == CraftChainPlanner.SourceChoice.CRAFT && sub != null && node.subIngredients() != null;
        if (!crafted) {
            needs.merge(ing.itemId(), needed, Integer::sum);
            return;
        }
        int yield = Math.max(1, sub.yield());
        int runs = Math.ceilDiv(needed, yield);
        List<Recipes.Ingredient> children = sub.ingredients();
        List<CraftChainPlanner.ChainNode> childNodes = node.subIngredients();
        for (int i = 0; i < children.size() && i < childNodes.size(); i++) {
            walk(children.get(i), childNodes.get(i), children.get(i).quantity() * runs, needs, steps, itemName);
        }
        // Post-order — a step's own inputs are already listed above it.
        steps.add(new PlanCraftStep(
                ing.itemId(), itemName.apply(ing.itemId()), runs * yield, sub.craftClass(), sub.level()));
    }

    /**
     * Clear the cheap side of the book for {@code needed} units, cheapest
     * first, and emit one material line per world touched. A remainder
     * past the fetched levels is located at the deepest one and priced
     * with {@link ListingBook#REMAINDER_ESCALATION} — matching the
     * valuation-side book walk.
     */
    static List<PlanCraftMaterial> allocate(
            int itemId,
            int needed,
            List<ItemDetail.PriceLevel> levels,
            IntFunction<String> itemName,
            IntFunction<String> worldName,
            Map<Integer, Integer> consumedByItem) {
        if (levels == null || levels.isEmpty() || needed <= 0) return List.of();
        int skip = Math.max(0, consumedByItem.getOrDefault(itemId, 0));
        consumedByItem.merge(itemId, needed, Integer::sum);
        var qtyByWorld = new LinkedHashMap<Integer, Integer>();
        var costByWorld = new LinkedHashMap<Integer, Long>();
        int remaining = needed;
        ItemDetail.PriceLevel last = levels.getFirst();
        for (var level : levels) {
            if (remaining <= 0) break;
            int available = Math.max(0, level.quantity());
            int skipped = Math.min(skip, available);
            skip -= skipped;
            available -= skipped;
            int take = Math.min(remaining, available);
            if (take > 0) {
                qtyByWorld.merge(level.worldId(), take, Integer::sum);
                costByWorld.merge(level.worldId(), (long) take * level.unitPrice(), Long::sum);
                remaining -= take;
            }
            last = level;
        }
        if (remaining > 0) {
            long escalated = (long) Math.ceil(last.unitPrice() * ListingBook.REMAINDER_ESCALATION);
            qtyByWorld.merge(last.worldId(), remaining, Integer::sum);
            costByWorld.merge(last.worldId(), remaining * escalated, Long::sum);
        }
        var out = new ArrayList<PlanCraftMaterial>(qtyByWorld.size());
        for (var e : qtyByWorld.entrySet()) {
            int qty = e.getValue();
            long cost = costByWorld.get(e.getKey());
            out.add(new PlanCraftMaterial(
                    itemId,
                    itemName.apply(itemId),
                    qty,
                    (int) Math.ceil(cost / (double) qty),
                    cost,
                    e.getKey(),
                    worldName.apply(e.getKey())));
        }
        return out;
    }
}
