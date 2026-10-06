/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.entity.OfferFilterTarget;
import de.chojo.lolorito.repository.DesynthResults;
import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.repository.Recipes;
import de.chojo.lolorito.value.ComponentPricing;
import de.chojo.lolorito.value.CraftChainPlanner;
import de.chojo.lolorito.value.ListingBook;
import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.UserPrefs;
import de.chojo.lolorito.value.Valuation;
import de.chojo.lolorito.value.ValueEngine;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Assembles the item detail response — name, per-world listings, fitted
 * home-world model, and (when data is present) the desynth + craft action
 * breakdowns with their scored valuations. All scoring goes through
 * {@link ValueEngine}; no SQL lives here.
 */
@Singleton
public class ItemDetailService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ItemDetailService.class);

    private final File config;
    private final ItemDetail repo;
    private final Recipes recipes;
    private final DesynthResults desynthResults;
    private final NameSupplier itemNames;
    private final ItemCatalog catalog;
    private final de.chojo.lolorito.value.MarketModelFitter fitter;
    private final de.chojo.lolorito.repository.MarketModels models;
    private final ResponseCache<CacheKey, Optional<Detail>> cache;

    @Inject
    public ItemDetailService(
            File config,
            ItemDetail repo,
            Recipes recipes,
            DesynthResults desynthResults,
            NameSupplier itemNames,
            ItemCatalog catalog,
            de.chojo.lolorito.value.MarketModelFitter fitter,
            de.chojo.lolorito.repository.MarketModels models) {
        this.config = config;
        this.repo = repo;
        this.recipes = recipes;
        this.desynthResults = desynthResults;
        this.itemNames = itemNames;
        this.catalog = catalog;
        this.fitter = fitter;
        this.models = models;
        this.cache = new ResponseCache<>(
                config.value().responseCacheSeconds(), config.value().responseCacheMaxSize());
    }

    private static String worldName(int worldId) {
        var w = Worlds.worldById(worldId);
        return w == null ? String.valueOf(worldId) : w.name();
    }

    private static ModelDto toModelDto(MarketModel m) {
        return new ModelDto(
                m.price().expectedPrice(),
                m.price().medianPrice(),
                m.price().sigma(),
                m.saleRate().lambdaP95(),
                m.saleRate().lambdaP100(),
                m.saleRate().lambdaP105(),
                m.lambdaUndercut(),
                m.ghostFraction(),
                m.sampleCount(),
                m.sufficient(),
                m.pooled(),
                m.fittedAt().toString());
    }

    private static ValuationDto toValuationDto(Valuation v) {
        return new ValuationDto(
                v.expectedNet(), v.sigmaNet(), v.evGross(), v.expectedTimeOnShelfHours(), v.evPerHour(), v.listRatio());
    }

    // -- Loading ------------------------------------------------------------

    /**
     * Everything about the caller that changes the response: locale,
     * craft skills (gates buy-vs-craft), ingredient-sourcing scope
     * (DC or region — the "buy anywhere cheaper" radius), and attention
     * fraction. All of it participates in the cache key.
     */
    public record ViewerContext(
            Language language, Map<String, Integer> craftLevels, OfferFilterTarget scope, double attentionFraction) {}

    /** Context with instance defaults — tests and user-less callers. */
    public ViewerContext defaultContext(Language language) {
        return new ViewerContext(language, Map.of(), OfferFilterTarget.DATA_CENTER, 0.25);
    }

    public Optional<Detail> load(int itemId, int homeWorldId, boolean hq) {
        return load(itemId, homeWorldId, hq, defaultContext(Language.ENGLISH));
    }

    public Optional<Detail> load(int itemId, int homeWorldId, boolean hq, Language language) {
        return load(itemId, homeWorldId, hq, defaultContext(language));
    }

    /**
     * Localised load — item, component, and ingredient names come back
     * in the context's language whenever the underlying name entry has
     * it, with English fallback.
     */
    public Optional<Detail> load(int itemId, int homeWorldId, boolean hq, ViewerContext ctx) {
        var levels = ctx.craftLevels() == null ? Map.<String, Integer>of() : ctx.craftLevels();
        var keyLevels = levels.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .sorted()
                .toList();
        var key =
                new CacheKey(itemId, homeWorldId, hq, ctx.language(), keyLevels, ctx.scope(), ctx.attentionFraction());
        return cache.get(key, k -> computeLoad(itemId, homeWorldId, hq, ctx));
    }

    public Optional<Detail> loadUncached(int itemId, int homeWorldId, boolean hq) {
        return computeLoad(itemId, homeWorldId, hq, defaultContext(Language.ENGLISH));
    }

    // -- Helpers ------------------------------------------------------------

    public void invalidateCache() {
        cache.invalidateAll();
    }

    public ResponseCache<CacheKey, Optional<Detail>> cache() {
        return cache;
    }

    private Optional<Detail> computeLoad(int itemId, int homeWorldId, boolean hq, ViewerContext ctx) {
        World home = Worlds.worldById(homeWorldId);
        if (home == null || home.dataCenter() == null) return Optional.empty();
        Language language = ctx.language();

        var model = repo.homeModel(homeWorldId, itemId, hq).orElse(null);
        if (model == null) {
            model = fitOnDemand(homeWorldId, itemId, hq);
        }
        // DC-scoped listings — travelling between data centers is
        // substantial effort, so prices outside the home DC are neither a
        // realistic buy nor competition worth undercutting. (A deliberate
        // reversal of the earlier region-wide choice.)
        var listings = repo.listings(itemId, home.dataCenter().id(), hq, 60);
        var prefs = new UserPrefs(config.value().mbTaxFor(home.dataCenter().id()), ctx.attentionFraction(), 30.0);

        DesynthBreakdown desynth = loadDesynth(itemId, homeWorldId, prefs, listings, language);
        List<CraftBreakdown> crafts = loadCrafts(itemId, homeWorldId, home, hq, prefs, model, ctx);

        return Optional.of(new Detail(
                itemId,
                nameOf(itemId, language),
                catalog.categoryFor(itemId),
                catalog.descriptionFor(itemId),
                hq,
                home.id(),
                home.name(),
                home.dataCenter().name(),
                model == null ? null : toModelDto(model),
                listings.stream()
                        .map(l -> new ListingDto(
                                l.worldId(),
                                worldName(l.worldId()),
                                l.unitPrice(),
                                l.quantity(),
                                l.hq(),
                                l.reviewedAt().toString()))
                        .toList(),
                desynth,
                crafts));
    }

    private DesynthBreakdown loadDesynth(
            int itemId, int homeWorldId, UserPrefs prefs, List<ItemDetail.ListingRow> listings, Language language) {
        var components = desynthResults.findBySource(itemId);
        if (components.isEmpty()) return null;

        var pricings = new ArrayList<ComponentPricing>();
        var componentDtos = new ArrayList<DesynthComponentDto>();
        for (var c : components) {
            var m = repo.homeModel(homeWorldId, c.componentItemId(), false).orElse(null);
            pricings.add(new ComponentPricing(c.componentItemId(), c.avgQty(), m, null));
            double expectedNet = m == null ? 0.0 : m.price().expectedPrice() * (1.0 - prefs.mbTax()) * c.avgQty();
            componentDtos.add(new DesynthComponentDto(
                    c.componentItemId(),
                    nameOf(c.componentItemId(), language),
                    c.avgQty(),
                    m != null && m.sufficient(),
                    expectedNet));
        }

        int probeBuy = listings.stream()
                .mapToInt(ItemDetail.ListingRow::unitPrice)
                .min()
                .orElse(0);
        var val = ValueEngine.valueDesynth(probeBuy, 1, pricings, prefs).orElse(null);
        var meta = desynthResults.sourceOf(itemId).orElse(null);
        return new DesynthBreakdown(
                meta == null ? null : meta.desynthClass(),
                meta == null ? null : meta.desynthLevel(),
                componentDtos,
                val == null ? null : toValuationDto(val));
    }

    private List<CraftBreakdown> loadCrafts(
            int productItemId,
            int homeWorldId,
            World home,
            boolean hq,
            UserPrefs prefs,
            MarketModel productModel,
            ViewerContext ctx) {
        var recipesForProduct = recipes.findByProduct(productItemId);
        // No product model → the ingredient breakdown and buy costs still
        // render (valuation stays null). An empty craft tab on every item
        // without a fitted model made the feature look absent entirely.
        if (recipesForProduct.isEmpty()) return List.of();
        Language language = ctx.language();

        // HQ-aware valuation: a craft comes out HQ with the caller's HQ
        // chance and sells against the HQ model, the rest sells NQ. The
        // page's own quality flag decides which model we already hold.
        MarketModel nqModel =
                hq ? repo.homeModel(homeWorldId, productItemId, false).orElse(null) : productModel;
        MarketModel hqModel = hq
                ? productModel
                : repo.homeModel(homeWorldId, productItemId, true).orElse(null);
        // Crafting always assumes the crafter meets the HQ thresholds:
        // the HQ outcome is certain wherever an HQ model exists, and the
        // NQ model only carries products that cannot be HQ at all.
        double hqChance = 1.0;

        // Ingredient sourcing radius follows the caller's offer-filter
        // scope — "buy anywhere cheaper" means the region when the user
        // shops region-wide, the home DC otherwise.
        boolean region = ctx.scope() == OfferFilterTarget.REGION;
        Integer scopeDc = region ? null : home.dataCenter().id();
        String scopeRegion = region ? home.dataCenter().region().name() : null;

        var out = new ArrayList<CraftBreakdown>();
        for (var r : recipesForProduct) {
            // One batched cheap-book lookup for every id reachable in this
            // recipe's DAG — the chain planner then picks buy-vs-craft per
            // node without any further DB round-trips. Depth-aware: the
            // effective unit cost walks the price steps for the quantity
            // the recipe actually needs, instead of pretending the whole
            // need fills at a possibly one-unit min-price row.
            var reachable = collectReachableItemIds(r);
            var needed = neededQuantities(r);
            var book = repo.cheapBook(
                    scopeDc, scopeRegion, reachable, false, config.value().listingFreshnessHours(), BOOK_LEVELS);
            java.util.function.IntFunction<Integer> cost =
                    id -> ListingBook.unitCostFor(book.get(id), needed.getOrDefault(id, 1));
            // Empty levels → informational, ungated. Non-empty → sub-crafts
            // the caller can't perform (class or level) resolve to BUY.
            var chain = CraftChainPlanner.plan(
                    r, cost, config.planner().craftChainMaxDepth(), CraftChainPlanner.levelGate(ctx.craftLevels()));

            var ingDtos = chain.ingredients().stream()
                    .map(n -> toIngredientDto(n, language))
                    .toList();
            var val = chain.perProductCost() == null
                    ? null
                    : ValueEngine.valueCraftMixed(
                                    nqModel,
                                    hqModel,
                                    hqChance,
                                    1,
                                    chain.perProductCost(),
                                    prefs,
                                    config.value().craftSecondsPerUnit())
                            .orElse(null);
            out.add(new CraftBreakdown(
                    r.id(), r.craftClass(), r.level(), r.yield(), ingDtos, val == null ? null : toValuationDto(val)));
        }
        return out;
    }

    /** Price steps fetched per ingredient — deep enough that realistic needs rarely hit the remainder escalation. */
    private static final int BOOK_LEVELS = 30;

    /**
     * The refit worker walks a large backlog; a user looking at a specific
     * item shouldn't wait for the rotation to reach it. One key fit is two
     * queries plus pure math — cheap enough to do inline, and the result
     * is persisted so the next request (and the worker) reuses it.
     */
    private MarketModel fitOnDemand(int homeWorldId, int itemId, boolean hq) {
        try {
            var fitted =
                    fitter.fit(homeWorldId, itemId, hq, java.time.Instant.now()).orElse(null);
            if (fitted != null) models.upsert(fitted);
            return fitted;
        } catch (Exception e) {
            log.debug(
                    "On-demand fit failed for (world={}, item={}, hq={}): {}", homeWorldId, itemId, hq, e.getMessage());
            return null;
        }
    }

    /** Walk every craftable descendant so the batched price lookup catches them all. */
    private List<Integer> collectReachableItemIds(Recipes.Recipe root) {
        Set<Integer> seen = new HashSet<>();
        collect(root, seen, config.planner().craftChainMaxDepth());
        return List.copyOf(seen);
    }

    private static void collect(Recipes.Recipe recipe, Set<Integer> seen, int depth) {
        if (recipe == null) return;
        for (var ing : recipe.ingredients()) {
            if (!seen.add(ing.itemId())) continue;
            if (depth > 0 && ing.recipeToCraftIt() != null) {
                collect(ing.recipeToCraftIt(), seen, depth - 1);
            }
        }
    }

    /**
     * Direct requirement per reachable item for one product — the depth
     * the book walk should price. When an item appears in several places
     * of the DAG the largest single requirement wins; exact need depends
     * on which buy-vs-craft branch the planner picks, and this bound is
     * far closer to reality than the old "price one unit".
     */
    static Map<Integer, Integer> neededQuantities(Recipes.Recipe root) {
        var out = new java.util.HashMap<Integer, Integer>();
        collectNeeds(root, 1, out, DEFAULT_NEED_DEPTH);
        return out;
    }

    private static final int DEFAULT_NEED_DEPTH = 3;

    /**
     * Requirements are multiplied through the sub-craft runs above them
     * ({@code ceil(need / yield)} runs, matching the BOM math) instead of
     * the old "max direct requirement" bound, which under-priced deep
     * ingredients needed many times over. When an item appears in several
     * branches the largest single-path need wins — the exact figure still
     * depends on which buy-vs-craft branch the planner picks.
     */
    private static void collectNeeds(Recipes.Recipe recipe, int runs, Map<Integer, Integer> out, int depth) {
        if (recipe == null) return;
        for (var ing : recipe.ingredients()) {
            int needed = ing.quantity() * Math.max(1, runs);
            out.merge(ing.itemId(), needed, Math::max);
            var sub = ing.recipeToCraftIt();
            if (depth > 0 && sub != null) {
                int subRuns = Math.ceilDiv(needed, Math.max(1, sub.yield()));
                collectNeeds(sub, subRuns, out, depth - 1);
            }
        }
    }

    private CraftIngredientDto toIngredientDto(CraftChainPlanner.ChainNode node, Language language) {
        List<CraftIngredientDto> children = node.subIngredients() == null
                ? null
                : node.subIngredients().stream()
                        .map(n -> toIngredientDto(n, language))
                        .toList();
        return new CraftIngredientDto(
                node.itemId(),
                nameOf(node.itemId(), language),
                node.quantity(),
                node.buyPrice(),
                node.craftPerUnit(),
                node.chosen().name().toLowerCase(),
                children,
                node.subRecipeClass() == null || node.subRecipeClass().isBlank() ? null : node.subRecipeClass(),
                node.subRecipeLevel() > 0 ? node.subRecipeLevel() : null,
                node.depthCapped());
    }

    private String nameOf(int itemId, Language language) {
        if (itemNames == null) return String.valueOf(itemId);
        var name = itemNames.fromId(itemId);
        if (name == null) return String.valueOf(itemId);
        String localised = name.get(language);
        if (localised != null && !localised.isBlank()) return localised;
        String english = name.get(Language.ENGLISH);
        return english == null ? String.valueOf(itemId) : english;
    }

    /**
     * Cache key — the request shape. {@code craftLevels} is the sorted
     * {@code class:level} encoding so the same skill map always hashes
     * identically.
     */
    public record CacheKey(
            int itemId,
            int homeWorldId,
            boolean hq,
            Language language,
            List<String> craftLevels,
            OfferFilterTarget scope,
            double attentionFraction) {}

    // -- DTO shapes ---------------------------------------------------------

    public record Detail(
            int itemId,
            String itemName,
            String category,
            String description,
            boolean hq,
            int homeWorldId,
            String homeWorldName,
            String dataCenter,
            ModelDto model,
            List<ListingDto> listings,
            DesynthBreakdown desynth,
            List<CraftBreakdown> crafts) {}

    public record ModelDto(
            double expectedPrice,
            double medianPrice,
            double sigma,
            double lambdaAggressive,
            double lambdaMedian,
            double lambdaAbove,
            double lambdaUndercut,
            double ghostFraction,
            int sampleCount,
            boolean sufficient,
            boolean pooled,
            String fittedAt) {}

    public record ListingDto(
            int worldId, String worldName, int unitPrice, int quantity, boolean hq, String reviewedAt) {}

    public record ValuationDto(
            double expectedNet,
            double sigmaNet,
            double evGross,
            double expectedTimeOnShelfHours,
            double evPerHour,
            /** Recommended list price as a multiple of the market median. */
            double listRatio) {}

    public record DesynthBreakdown(
            String desynthClass, Integer desynthLevel, List<DesynthComponentDto> components, ValuationDto valuation) {}

    public record DesynthComponentDto(
            int itemId, String itemName, double avgQty, boolean modelSufficient, double expectedNet) {}

    public record CraftBreakdown(
            int recipeId,
            String craftClass,
            int level,
            int yield,
            List<CraftIngredientDto> ingredients,
            ValuationDto valuation) {}

    /**
     * One ingredient in a craft breakdown. {@code chosenSource} is one of
     * {@code buy} / {@code craft} / {@code unknown} — the planner's per-node
     * decision. {@code subIngredients} is populated only when the planner
     * walked deeper (i.e. the ingredient itself is craftable within the
     * depth cap), independent of which source was chosen; the SPA can
     * expand it either way.
     */
    public record CraftIngredientDto(
            int itemId,
            String itemName,
            int quantity,
            Integer cheapestBuy,
            Integer craftPerUnit,
            String chosenSource,
            List<CraftIngredientDto> subIngredients,
            /** Sub-recipe class name (carpenter, blacksmith, …) or null when buy-only. */
            String subRecipeClass,
            /** Sub-recipe level, or null when there's no sub-recipe. */
            Integer subRecipeLevel,
            /** True when a sub-recipe exists but lies past the explored depth. */
            boolean depthCapped) {}
}
