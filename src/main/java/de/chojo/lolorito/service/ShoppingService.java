/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.repository.Recipes;
import de.chojo.lolorito.value.CraftChainPlanner;
import de.chojo.lolorito.value.ListingBook;
import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.UserPrefs;
import de.chojo.lolorito.value.Valuation;
import de.chojo.lolorito.value.ValueEngine;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.Worlds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Quick shopping for a craft: "I want N of this item — where do I buy
 * the ingredients the cheapest?" Builds a shopping run shaped like the
 * planner's stops (world-grouped buy lines on the home DC) plus the
 * pre-craft steps, honoring two caller choices per node:
 *
 * <ul>
 *   <li><b>buy vs craft</b> per craftable intermediate — default is
 *       whichever the chain planner scored cheaper;</li>
 *   <li><b>HQ</b> per bought item (gated on the catalog's canBeHq) —
 *       the buy then prices against the HQ board.</li>
 * </ul>
 *
 * <p>Deliberately unrelated to the profit planner: no skill gating, no
 * EV solver — the caller already decided to craft; this is the cheapest
 * way to shop for it. The spread (materials vs expected sale of the
 * products) is still shown, always-HQ like every craft valuation.
 */
@Singleton
public class ShoppingService {

    /** Price steps fetched per item — matches the planner's depth. */
    private static final int BOOK_LEVELS = 30;

    private final File config;
    private final Recipes recipes;
    private final ItemDetail itemDetail;
    private final ItemCatalog itemCatalog;
    private final NameSupplier itemNames;
    private final de.chojo.lolorito.repository.MarketModels marketModels;

    @Inject
    public ShoppingService(
            File config,
            Recipes recipes,
            ItemDetail itemDetail,
            ItemCatalog itemCatalog,
            NameSupplier itemNames,
            de.chojo.lolorito.repository.MarketModels marketModels) {
        this.config = config;
        this.recipes = recipes;
        this.itemDetail = itemDetail;
        this.itemCatalog = itemCatalog;
        this.itemNames = itemNames;
        this.marketModels = marketModels;
    }

    /** Caller decision for one craftable node. */
    public enum Decision {
        BUY,
        CRAFT
    }

    /** One buy line — {@code qty} of the item on {@code worldName}, HQ when marked. */
    public record ShoppingLine(
            int itemId,
            String itemName,
            boolean hq,
            int qty,
            int unitPrice,
            long totalCost,
            int worldId,
            String worldName,
            boolean canBeHq) {}

    /** One world visit of the shopping run, ordered biggest spend first. */
    public record ShoppingStop(int worldId, String worldName, long cost, List<ShoppingLine> lines) {}

    /** A pre-craft: make {@code qty} of the item before the head craft consumes it. */
    public record ShoppingStep(int itemId, String itemName, int qty, String craftClass, int craftLevel) {}

    /**
     * One node of the decision tree the SPA renders toggles on.
     * {@code children} are present for craftable nodes regardless of the
     * decision, so flipping buy→craft is an informed choice.
     *
     * @param buyUnitCost  depth-aware unit cost of buying {@code qty} on
     *                     the chosen-quality board; null when unlisted
     * @param craftUnitCost per-unit cost of crafting it instead; null
     *                     when not craftable or unpriceable
     * @param depthCapped  true when a sub-recipe exists past the explored
     *                     depth, so the node is offered as a buy only
     */
    public record ShoppingNode(
            int itemId,
            String itemName,
            int qty,
            boolean canBeHq,
            boolean hq,
            boolean craftable,
            String decision,
            Integer buyUnitCost,
            Integer craftUnitCost,
            String craftClass,
            Integer craftLevel,
            List<ShoppingNode> children,
            boolean depthCapped) {}

    /**
     * One recipe that produces the requested item, offered so the caller
     * can switch between recipes for the same product.
     */
    public record RecipeOption(int recipeId, String craftClass, int level, int yield) {}

    /**
     * The full response. {@code recipes} lists every recipe for the
     * product, in repository order. {@code valuation} (always-HQ, over {@code count}
     * products at {@code totalCost / count} apiece) is null when the
     * product has no sufficient home model — the shopping list still
     * works, the spread simply can't be shown.
     */
    public record ShoppingPlan(
            int itemId,
            String itemName,
            int count,
            int runs,
            int yield,
            int recipeId,
            String craftClass,
            int craftLevel,
            boolean productCanBeHq,
            List<RecipeOption> recipes,
            List<ShoppingStop> stops,
            List<ShoppingStep> preCrafts,
            List<ShoppingNode> tree,
            long totalCost,
            Valuation valuation) {}

    /**
     * Build the shopping plan. Empty when the item has no recipe or the
     * home world is unknown.
     *
     * @param recipeId  optional — when the product has several recipes;
     *                  defaults to the first
     * @param count     finished items wanted; runs = ceil(count / yield)
     * @param hqItemIds items to buy from the HQ board (non-HQ-able ids
     *                  are ignored)
     * @param overrides per-item buy/craft decisions; absent = cheapest
     */
    public Optional<ShoppingPlan> plan(
            int homeWorldId,
            int productItemId,
            Integer recipeId,
            int count,
            Set<Integer> hqItemIds,
            Map<Integer, Decision> overrides) {
        var home = Worlds.worldById(homeWorldId);
        if (home == null || home.dataCenter() == null || count <= 0) return Optional.empty();

        var candidates = recipes.findByProduct(productItemId);
        if (candidates.isEmpty()) return Optional.empty();
        Recipes.Recipe recipe = candidates.stream()
                .filter(r -> recipeId == null || r.id() == recipeId)
                .findFirst()
                .orElse(candidates.getFirst());

        int yield = Math.max(1, recipe.yield());
        int runs = Math.ceilDiv(count, yield);
        int dcId = home.dataCenter().id();
        int freshHours = config.value().listingFreshnessHours();

        // Books: NQ for everything reachable, HQ only where marked. The
        // marked quality also drives the chain's buy-vs-craft default.
        List<Integer> reachable = collectReachable(recipe);
        var hqMarked = hqItemIds == null ? Set.<Integer>of() : hqItemIds;
        var bookNq = itemDetail.cheapBook(dcId, null, reachable, false, freshHours, BOOK_LEVELS);
        var hqWanted = reachable.stream()
                .filter(hqMarked::contains)
                .filter(itemCatalog::canBeHq)
                .toList();
        var bookHq = hqWanted.isEmpty()
                ? Map.<Integer, List<ItemDetail.PriceLevel>>of()
                : itemDetail.cheapBook(dcId, null, hqWanted, true, freshHours, BOOK_LEVELS);

        var needed = scaledNeeds(recipe, runs);
        java.util.function.IntFunction<Integer> cost =
                id -> ListingBook.unitCostFor(bookFor(id, hqMarked, bookNq, bookHq), needed.getOrDefault(id, 1));
        var chain = CraftChainPlanner.plan(recipe, cost, config.planner().craftChainMaxDepth(), CraftChainPlanner.ANY);

        // Walk recipe × chain, honoring overrides: aggregate buy needs
        // per (item, hq) and collect the pre-craft steps post-order.
        var buyNeeds = new LinkedHashMap<Integer, Integer>();
        var steps = new ArrayList<ShoppingStep>();
        var tree = new ArrayList<ShoppingNode>();
        var effective = overrides == null ? Map.<Integer, Decision>of() : overrides;
        for (int i = 0;
                i < recipe.ingredients().size() && i < chain.ingredients().size();
                i++) {
            var ing = recipe.ingredients().get(i);
            tree.add(
                    walk(ing, chain.ingredients().get(i), ing.quantity() * runs, effective, hqMarked, buyNeeds, steps));
        }

        // Allocate every buy need against its board, cheapest-first, and
        // group the world segments into stops.
        var linesByWorld = new LinkedHashMap<Integer, List<ShoppingLine>>();
        for (var need : buyNeeds.entrySet()) {
            int itemId = need.getKey();
            boolean hq = isHqBuy(itemId, hqMarked);
            var segments = CraftBomBuilder.allocate(
                    itemId,
                    need.getValue(),
                    bookFor(itemId, hqMarked, bookNq, bookHq),
                    this::nameOf,
                    ShoppingService::worldNameOf,
                    new LinkedHashMap<>());
            for (var m : segments) {
                linesByWorld
                        .computeIfAbsent(m.worldId(), w -> new ArrayList<>())
                        .add(new ShoppingLine(
                                m.itemId(),
                                m.itemName(),
                                hq,
                                m.qty(),
                                m.unitPrice(),
                                m.totalCost(),
                                m.worldId(),
                                m.worldName(),
                                itemCatalog.canBeHq(m.itemId())));
            }
        }
        var stops = new ArrayList<ShoppingStop>(linesByWorld.size());
        long totalCost = 0;
        for (var e : linesByWorld.entrySet()) {
            long cost0 =
                    e.getValue().stream().mapToLong(ShoppingLine::totalCost).sum();
            totalCost += cost0;
            stops.add(new ShoppingStop(e.getKey(), worldNameOf(e.getKey()), cost0, List.copyOf(e.getValue())));
        }
        stops.sort(java.util.Comparator.comparingLong(ShoppingStop::cost).reversed());

        // The spread — what the products fetch at home vs what the
        // shopping run costs. Always-HQ, like every craft valuation.
        Valuation valuation = null;
        if (totalCost > 0) {
            MarketModel nqModel =
                    marketModels.find(homeWorldId, productItemId, false).orElse(null);
            MarketModel hqModel =
                    marketModels.find(homeWorldId, productItemId, true).orElse(null);
            double perProduct = totalCost / (double) count;
            valuation = ValueEngine.valueCraftMixed(
                            nqModel,
                            hqModel,
                            1.0,
                            count,
                            perProduct,
                            prefs(dcId),
                            config.value().craftSecondsPerUnit())
                    .orElse(null);
        }

        return Optional.of(new ShoppingPlan(
                productItemId,
                nameOf(productItemId),
                count,
                runs,
                yield,
                recipe.id(),
                recipe.craftClass(),
                recipe.level(),
                itemCatalog.canBeHq(productItemId),
                candidates.stream()
                        .map(r -> new RecipeOption(r.id(), r.craftClass(), r.level(), r.yield()))
                        .toList(),
                List.copyOf(stops),
                List.copyOf(steps),
                List.copyOf(tree),
                totalCost,
                valuation));
    }

    /**
     * One ingredient node: resolve the effective decision, recurse when
     * crafted (children needs multiply through the sub-runs), record the
     * buy need otherwise. Children are always materialised for craftable
     * nodes so the SPA can preview the flip.
     */
    private ShoppingNode walk(
            Recipes.Ingredient ing,
            CraftChainPlanner.ChainNode node,
            int needed,
            Map<Integer, Decision> overrides,
            Set<Integer> hqMarked,
            Map<Integer, Integer> buyNeeds,
            List<ShoppingStep> steps) {
        Recipes.Recipe sub = ing.recipeToCraftIt();
        boolean craftable = sub != null && node.subIngredients() != null;
        Decision decision = overrides.get(ing.itemId());
        if (decision == null) {
            decision =
                    craftable && node.chosen() == CraftChainPlanner.SourceChoice.CRAFT ? Decision.CRAFT : Decision.BUY;
        }
        if (!craftable) decision = Decision.BUY;

        var children = new ArrayList<ShoppingNode>();
        if (craftable) {
            int subYield = Math.max(1, sub.yield());
            int subRuns = Math.ceilDiv(needed, subYield);
            boolean crafted = decision == Decision.CRAFT;
            for (int i = 0;
                    i < sub.ingredients().size() && i < node.subIngredients().size();
                    i++) {
                var child = sub.ingredients().get(i);
                var childNode = node.subIngredients().get(i);
                int childNeed = child.quantity() * subRuns;
                if (crafted) {
                    children.add(walk(child, childNode, childNeed, overrides, hqMarked, buyNeeds, steps));
                } else {
                    // Preview only — no needs recorded, no steps emitted.
                    children.add(preview(child, childNode, childNeed, overrides, hqMarked));
                }
            }
            if (crafted) {
                steps.add(new ShoppingStep(
                        ing.itemId(), nameOf(ing.itemId()), subRuns * subYield, sub.craftClass(), sub.level()));
            }
        }
        if (decision == Decision.BUY) {
            buyNeeds.merge(ing.itemId(), needed, Integer::sum);
        }
        return node(ing, node, needed, decision, craftable, hqMarked, children, sub);
    }

    /** Tree node without side effects — renders the not-taken branch. */
    private ShoppingNode preview(
            Recipes.Ingredient ing,
            CraftChainPlanner.ChainNode node,
            int needed,
            Map<Integer, Decision> overrides,
            Set<Integer> hqMarked) {
        Recipes.Recipe sub = ing.recipeToCraftIt();
        boolean craftable = sub != null && node.subIngredients() != null;
        Decision decision = overrides.getOrDefault(
                ing.itemId(),
                craftable && node.chosen() == CraftChainPlanner.SourceChoice.CRAFT ? Decision.CRAFT : Decision.BUY);
        if (!craftable) decision = Decision.BUY;
        var children = new ArrayList<ShoppingNode>();
        if (craftable) {
            int subRuns = Math.ceilDiv(needed, Math.max(1, sub.yield()));
            for (int i = 0;
                    i < sub.ingredients().size() && i < node.subIngredients().size();
                    i++) {
                var child = sub.ingredients().get(i);
                children.add(
                        preview(child, node.subIngredients().get(i), child.quantity() * subRuns, overrides, hqMarked));
            }
        }
        return node(ing, node, needed, decision, craftable, hqMarked, children, sub);
    }

    private ShoppingNode node(
            Recipes.Ingredient ing,
            CraftChainPlanner.ChainNode chainNode,
            int needed,
            Decision decision,
            boolean craftable,
            Set<Integer> hqMarked,
            List<ShoppingNode> children,
            Recipes.Recipe sub) {
        return new ShoppingNode(
                ing.itemId(),
                nameOf(ing.itemId()),
                needed,
                itemCatalog.canBeHq(ing.itemId()),
                isHqBuy(ing.itemId(), hqMarked),
                craftable,
                decision.name().toLowerCase(Locale.ROOT),
                chainNode.buyPrice(),
                chainNode.craftPerUnit(),
                sub == null ? null : sub.craftClass(),
                sub == null ? null : sub.level(),
                List.copyOf(children),
                chainNode.depthCapped());
    }

    private boolean isHqBuy(int itemId, Set<Integer> hqMarked) {
        return hqMarked.contains(itemId) && itemCatalog.canBeHq(itemId);
    }

    private List<ItemDetail.PriceLevel> bookFor(
            int itemId,
            Set<Integer> hqMarked,
            Map<Integer, List<ItemDetail.PriceLevel>> bookNq,
            Map<Integer, List<ItemDetail.PriceLevel>> bookHq) {
        return isHqBuy(itemId, hqMarked) ? bookHq.get(itemId) : bookNq.get(itemId);
    }

    /** Per-item needs for {@code runs} head-recipe runs, multiplied through sub-craft runs. */
    private static Map<Integer, Integer> scaledNeeds(Recipes.Recipe recipe, int runs) {
        var perRun = ItemDetailService.neededQuantities(recipe);
        var out = new LinkedHashMap<Integer, Integer>(perRun.size());
        for (var e : perRun.entrySet()) out.put(e.getKey(), e.getValue() * Math.max(1, runs));
        return out;
    }

    private static List<Integer> collectReachable(Recipes.Recipe root) {
        var seen = new java.util.LinkedHashSet<Integer>();
        collect(root, seen);
        return List.copyOf(seen);
    }

    private static void collect(Recipes.Recipe r, Set<Integer> seen) {
        for (var ing : r.ingredients()) {
            seen.add(ing.itemId());
            var sub = ing.recipeToCraftIt();
            if (sub != null) collect(sub, seen);
        }
    }

    private UserPrefs prefs(int dcId) {
        return new UserPrefs(config.value().mbTaxFor(dcId), 0.25, 30.0);
    }

    private static String worldNameOf(int worldId) {
        var world = Worlds.worldById(worldId);
        return world == null ? String.valueOf(worldId) : world.name();
    }

    private String nameOf(int itemId) {
        if (itemNames == null) return String.valueOf(itemId);
        var name = itemNames.fromId(itemId);
        return name == null ? String.valueOf(itemId) : name.get(Language.ENGLISH);
    }
}
