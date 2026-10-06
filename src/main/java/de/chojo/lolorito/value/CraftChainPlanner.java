/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import de.chojo.lolorito.repository.Recipes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Walks the recipe DAG the {@link Recipes} repo already loads and
 * decides, per ingredient, whether it is cheaper to buy it at its current
 * cheapest listing or to craft it from its own ingredients (which may in
 * turn be crafted). The result is a tree of {@link ChainNode}s the SPA
 * can render as an expandable breakdown, plus a total per-product cost the
 * caller passes to {@link ValueEngine#valueCraft}.
 *
 * <p>Two safety rails: a caller-provided {@code maxDepth} caps the
 * recursion, and a visited-id set guards against cycles in the DAG (FFXIV
 * shouldn't have any, but the recipe loader already carries the same
 * guard and this one keeps us defensive at valuation time too).
 *
 * <p>The chosen source per node is pure: {@code CRAFT} wins only when
 * every sub-ingredient has a known price and the crafted per-product cost
 * is strictly cheaper than the buy price. Ties break in favour of
 * {@code BUY} — fewer clicks for the player.
 */
public final class CraftChainPlanner {

    /** Default recursion cap — enough for pretty much every FFXIV chain we've seen in practice. */
    public static final int DEFAULT_MAX_DEPTH = 3;

    private CraftChainPlanner() {}

    /**
     * Whether the caller can perform a craft of {@code craftClass} at
     * {@code level}. {@link #ANY} passes everything — the informational,
     * ungated view.
     */
    @FunctionalInterface
    public interface CraftGate {
        boolean canCraft(String craftClass, int level);
    }

    /** Permissive gate — every craft is considered performable. */
    public static final CraftGate ANY = (craftClass, level) -> true;

    /**
     * Gate from the caller's stored per-class levels: a craft passes when
     * the class is recorded AND the recorded level meets the recipe's.
     * An empty map degrades to {@link #ANY} — no recorded skills means
     * the caller hasn't told us anything, not that they can craft nothing;
     * callers wanting strictness handle the empty case themselves.
     */
    public static CraftGate levelGate(java.util.Map<String, Integer> craftLevels) {
        if (craftLevels == null || craftLevels.isEmpty()) return ANY;
        return (craftClass, level) -> craftLevels.getOrDefault(craftClass, -1) >= level;
    }

    /**
     * Plan sourcing for a whole recipe. Returns the top-level chain
     * (one node per ingredient) plus the per-product cost after each
     * ingredient's cheapest source is chosen. When any ingredient can't
     * be priced at all — no listing and no craftable sub-recipe — the
     * per-product cost is {@code null}.
     */
    public static ChainPlan plan(Recipes.Recipe recipe, IntFunction<Integer> cheapestFor, int maxDepth) {
        return plan(recipe, cheapestFor, maxDepth, ANY);
    }

    /**
     * Class-set compatibility overload: non-empty set → sub-crafts of
     * other classes resolve to BUY, at any level. Prefer
     * {@link #plan(Recipes.Recipe, IntFunction, int, CraftGate)} with
     * {@link #levelGate} where per-class levels are known.
     */
    public static ChainPlan plan(
            Recipes.Recipe recipe, IntFunction<Integer> cheapestFor, int maxDepth, Set<String> allowedCraftClasses) {
        CraftGate gate = allowedCraftClasses == null || allowedCraftClasses.isEmpty()
                ? ANY
                : (craftClass, level) -> allowedCraftClasses.contains(craftClass);
        return plan(recipe, cheapestFor, maxDepth, gate);
    }

    /**
     * Same as {@link #plan(Recipes.Recipe, IntFunction, int)} but gates
     * sub-craft consideration on the caller's abilities. A sub recipe the
     * gate rejects is treated like there's no sub-recipe at all — the
     * caller can't craft it, so buying is the only realistic path.
     */
    public static ChainPlan plan(
            Recipes.Recipe recipe, IntFunction<Integer> cheapestFor, int maxDepth, CraftGate gate) {
        int yield = Math.max(1, recipe.yield());
        var nodes = new ArrayList<ChainNode>(recipe.ingredients().size());
        Double perProductCost = 0.0;
        for (var ing : recipe.ingredients()) {
            var node = resolve(ing, cheapestFor, new HashSet<>(), maxDepth, gate == null ? ANY : gate);
            nodes.add(node);
            if (perProductCost == null || node.exactUnitCost() == null) {
                perProductCost = null;
            } else {
                perProductCost += (node.exactUnitCost() * ing.quantity()) / (double) yield;
            }
        }
        return new ChainPlan(nodes, perProductCost);
    }

    /** Materialise one ingredient — chooses cheaper of buy vs craft, recurses into craft. */
    private static ChainNode resolve(
            Recipes.Ingredient ing,
            IntFunction<Integer> cheapestFor,
            Set<Integer> visited,
            int remainingDepth,
            CraftGate gate) {
        Integer buyPrice = cheapestFor.apply(ing.itemId());
        Recipes.Recipe sub = ing.recipeToCraftIt();

        // No sub-recipe or budget exhausted or skill gate locks us out → buy only.
        boolean subGatedByClass = sub != null && !gate.canCraft(sub.craftClass(), sub.level());
        if (sub == null || subGatedByClass || remainingDepth <= 0 || !visited.add(ing.itemId())) {
            SourceChoice choice = buyPrice == null ? SourceChoice.UNKNOWN : SourceChoice.BUY;
            return new ChainNode(
                    ing.itemId(),
                    ing.quantity(),
                    buyPrice,
                    null,
                    null,
                    null,
                    choice,
                    sub == null ? "" : sub.craftClass(),
                    sub == null ? 0 : sub.level(),
                    sub != null && !subGatedByClass && remainingDepth <= 0);
        }

        try {
            int subYield = Math.max(1, sub.yield());
            var children = new ArrayList<ChainNode>(sub.ingredients().size());
            Double subPerProduct = 0.0;
            for (var child : sub.ingredients()) {
                var childNode = resolve(child, cheapestFor, visited, remainingDepth - 1, gate);
                children.add(childNode);
                if (subPerProduct == null || childNode.exactUnitCost() == null) {
                    subPerProduct = null;
                } else {
                    subPerProduct += (childNode.exactUnitCost() * child.quantity()) / (double) subYield;
                }
            }
            // Display cost rounds per node; the exact double flows up the
            // chain so ceil() doesn't accumulate once per level.
            Integer craftCost = subPerProduct == null ? null : (int) Math.ceil(subPerProduct);
            SourceChoice choice = decide(buyPrice, subPerProduct);
            return new ChainNode(
                    ing.itemId(),
                    ing.quantity(),
                    buyPrice,
                    craftCost,
                    subPerProduct,
                    children,
                    choice,
                    sub.craftClass(),
                    sub.level(),
                    false);
        } finally {
            visited.remove(ing.itemId());
        }
    }

    private static SourceChoice decide(Integer buyPrice, Double craftCostExact) {
        if (buyPrice == null && craftCostExact == null) return SourceChoice.UNKNOWN;
        if (craftCostExact == null) return SourceChoice.BUY;
        if (buyPrice == null) return SourceChoice.CRAFT;
        return craftCostExact < buyPrice ? SourceChoice.CRAFT : SourceChoice.BUY;
    }

    /** Per-ingredient decision. {@code UNKNOWN} means neither a price nor a viable sub-craft. */
    public enum SourceChoice {
        BUY,
        CRAFT,
        UNKNOWN
    }

    /**
     * One node in the sourcing plan. {@code unitCost()} returns the cost of
     * the chosen source per one unit of the ingredient, or {@code null}
     * when the source is {@code UNKNOWN}. {@code depthCapped} is true when a
     * craftable sub-recipe exists but was not explored because the depth
     * budget ran out, so the node fell back to buying.
     */
    public record ChainNode(
            int itemId,
            int quantity,
            Integer buyPrice,
            Integer craftPerUnit,
            /** Unrounded craft cost — internal accumulation only; the DTO ships {@link #craftPerUnit}. */
            Double craftPerUnitExact,
            List<ChainNode> subIngredients,
            SourceChoice chosen,
            /**
             * When a sub-recipe exists, this is its {@code craftClass}
             * even if the SPA elected to buy this ingredient. Empty when
             * the ingredient is buy-only. Lets the SPA filter the tree
             * to classes the user actually has.
             */
            String subRecipeClass,
            /** Sub-recipe level, or 0 when the ingredient has no sub-recipe. */
            int subRecipeLevel,
            boolean depthCapped) {

        /** True when this node or any node below it hit the depth cap. */
        public boolean anyDepthCapped() {
            if (depthCapped) return true;
            return subIngredients != null && subIngredients.stream().anyMatch(ChainNode::anyDepthCapped);
        }

        public Integer unitCost() {
            return switch (chosen) {
                case BUY -> buyPrice;
                case CRAFT -> craftPerUnit;
                case UNKNOWN -> null;
            };
        }

        /** Chosen-source cost without per-level rounding — cost accumulation uses this. */
        public Double exactUnitCost() {
            return switch (chosen) {
                case BUY -> buyPrice == null ? null : buyPrice.doubleValue();
                case CRAFT -> craftPerUnitExact;
                case UNKNOWN -> null;
            };
        }
    }

    /**
     * @param ingredients one node per top-level ingredient
     * @param perProductCost cost of one produced unit after each ingredient's
     *                       cheapest source is chosen. Null when at least one
     *                       ingredient couldn't be priced at all.
     */
    public record ChainPlan(List<ChainNode> ingredients, Double perProductCost) {

        /** True when any ingredient's sub-tree was cut short by the depth cap. */
        public boolean depthCapped() {
            return ingredients.stream().anyMatch(ChainNode::anyDepthCapped);
        }
    }
}
