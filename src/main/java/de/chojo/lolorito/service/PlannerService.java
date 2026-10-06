/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.planner.Candidate;
import de.chojo.lolorito.planner.Plan;
import de.chojo.lolorito.planner.PlanAction;
import de.chojo.lolorito.planner.PlanBuy;
import de.chojo.lolorito.planner.PlanCraft;
import de.chojo.lolorito.planner.PlanDesynth;
import de.chojo.lolorito.planner.PlanDesynthOutput;
import de.chojo.lolorito.planner.PlanStop;
import de.chojo.lolorito.planner.PlannerEngine;
import de.chojo.lolorito.planner.PlannerParams;
import de.chojo.lolorito.planner.WorldNode;
import de.chojo.lolorito.repository.DesynthResults;
import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.repository.MarketModels;
import de.chojo.lolorito.repository.Offers;
import de.chojo.lolorito.repository.Recipes;
import de.chojo.lolorito.universalis.WorldNames;
import de.chojo.lolorito.value.ComponentPricing;
import de.chojo.lolorito.value.CraftChainPlanner;
import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.UserPrefs;
import de.chojo.lolorito.value.ValueEngine;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.DataCenter;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.clamp;

/**
 * Business logic for {@code POST /api/v1/plan}. Pulls the cross-DC
 * candidates via {@link Offers}, prices each with {@link ValueEngine}, then
 * hands off to {@link PlannerEngine}. The route layer is just serialization
 * around this.
 */
@Singleton
public class PlannerService {
    private final File config;
    private final Offers offers;
    private final NameSupplier itemNames;
    private final ItemCatalog itemCatalog;
    private final Recipes recipes;
    private final ItemDetail itemDetail;
    private final DesynthResults desynthResults;
    private final MarketModels marketModels;
    private final ResponseCache<CacheKey, Plan> cache;

    @Inject
    public PlannerService(
            File config,
            Offers offers,
            NameSupplier itemNames,
            ItemCatalog itemCatalog,
            Recipes recipes,
            ItemDetail itemDetail,
            DesynthResults desynthResults,
            MarketModels marketModels) {
        this.config = config;
        this.offers = offers;
        this.itemNames = itemNames;
        this.itemCatalog = itemCatalog;
        this.recipes = recipes;
        this.itemDetail = itemDetail;
        this.desynthResults = desynthResults;
        this.marketModels = marketModels;
        this.cache = new ResponseCache<>(
                config.value().responseCacheSeconds(), config.value().responseCacheMaxSize());
    }

    /**
     * Same clamping as {@link #paramsFor(File, int, int, PlanRequest)} but keyed off a {@link ReplanRequest}.
     */
    public static PlannerParams paramsFor(File config, int homeWorldId, int homeDataCenterId, ReplanRequest req) {
        return paramsFor(
                config,
                homeWorldId,
                homeDataCenterId,
                new PlanRequest(
                        req.homeWorld(),
                        req.refreshHours(),
                        req.budget(),
                        req.inventorySlots(),
                        req.attentionBudgetHours(),
                        req.attentionFraction(),
                        req.hopWeightGilPerSecond(),
                        req.maxWorlds(),
                        req.candidateTopK(),
                        req.retainerSlots(),
                        req.retainerListingSlots(),
                        req.retainerListingStackTarget(),
                        req.retainerAttentionFraction(),
                        req.retainerShelfHoursThreshold(),
                        req.allowCrafts(),
                        req.allowDesynth()));
    }

    /**
     * Merge planner defaults from config with any request overrides. Every
     * field is clamped to a safe range.
     */
    public static PlannerParams paramsFor(File config, int homeWorldId, int homeDataCenterId, PlanRequest req) {
        var p = config.planner();
        return new PlannerParams(
                homeWorldId,
                homeDataCenterId,
                clamp(pickLong(req.budget, p.budget()), 1L, 1_000_000_000L),
                clamp(pickInt(req.inventorySlots, p.inventorySlots()), 1, 5000),
                clamp(pickDouble(req.attentionBudgetHours, p.attentionBudgetHours()), 0.05, 24.0),
                clamp(pickDouble(req.attentionFraction, p.attentionFraction()), 0.01, 1.0),
                clamp(pickInt(req.hopWeightGilPerSecond, p.hopWeightGilPerSecond()), 1, 100_000),
                clamp(pickInt(req.maxWorlds, p.maxWorlds()), 1, 8),
                clamp(pickInt(req.candidateTopK, p.candidateTopK()), 10, 2000),
                p.tDcSeconds(),
                p.tRegionSeconds(),
                p.ilpMaxNodes(),
                clamp(pickInt(req.retainerSlots, p.retainerSlots()), 0, 200),
                clamp(pickInt(req.retainerListingSlots, p.retainerListingSlots()), 0, 1000),
                clamp(pickInt(req.retainerListingStackTarget, p.retainerListingStackTarget()), 1, 999),
                clamp(pickDouble(req.retainerAttentionFraction, p.retainerAttentionFraction()), 0.0, 1.0),
                clamp(pickDouble(req.retainerShelfHoursThreshold, p.retainerShelfHoursThreshold()), 0.0, 168.0));
    }

    /**
     * Price steps fetched per item — deep enough that realistic needs
     * rarely outrun the book and hit the remainder escalation.
     */
    private static final int BOOK_LEVELS = 30;

    private static String candidateKey(Offers.Candidate r, World source) {
        DataCenter dc = source.dataCenter();
        return "%d-%d-%s-%s-%d"
                .formatted(source.id(), r.itemId(), r.hq() ? "hq" : "nq", dc == null ? "?" : dc.id(), r.buyPrice());
    }

    private static int pickInt(Integer v, int fallback) {
        return v == null ? fallback : v;
    }

    private static long pickLong(Long v, long fallback) {
        return v == null ? fallback : v;
    }

    private static double pickDouble(Double v, double fallback) {
        return v == null ? fallback : v;
    }

    /**
     * Score every fresh cross-DC listing on {@code params.homeDataCenterId}
     * and hand the top-K candidates to the joint solver. Results are
     * memoised — a burst of clicks on the SPA re-uses the last plan.
     */
    public Plan plan(PlannerParams params, int refreshHours) {
        return cache.get(new CacheKey(params, refreshHours), k -> computePlan(k.params(), k.refreshHours()));
    }

    /**
     * Plan variant that also considers CRAFT candidates. Not cached — the
     * skill map is per-user and would blow cache utility.
     *
     * <p>Semantics of {@code craftLevels}: {@code null} → no CRAFT
     * synthesis at all (equivalent to {@link #plan(PlannerParams, int)}).
     * Empty map → the caller explicitly opted into crafts but has no
     * stored skills — the fallback: every recipe is considered, ungated
     * ("I can craft, I just haven't recorded levels"). Non-empty map →
     * recipes and sub-crafts gate on class <em>and</em> level.
     */
    public Plan plan(PlannerParams params, int refreshHours, Map<String, Integer> craftLevels) {
        return plan(params, refreshHours, craftLevels, null);
    }

    /**
     * Full-lane plan: RESALE always, CRAFT when {@code craftLevels} is
     * non-null, DESYNTH when {@code desynthLevels} is non-null. Both level
     * maps share the craft semantics documented above: null → lane off,
     * empty → ungated fallback, non-empty → class + level gating.
     */
    public Plan plan(
            PlannerParams params,
            int refreshHours,
            Map<String, Integer> craftLevels,
            Map<String, Integer> desynthLevels) {
        if (craftLevels == null && desynthLevels == null) {
            return plan(params, refreshHours);
        }
        var homeWorld = Worlds.worldById(params.homeWorldId());
        if (homeWorld == null || homeWorld.dataCenter() == null) {
            throw new IllegalArgumentException("unknown home world: " + params.homeWorldId());
        }
        var homeNode = homeNodeOf(homeWorld);
        var resale = loadCandidates(params, refreshHours);
        var craft = loadCraftSyntheses(params, craftLevels);
        var desynth = loadDesynthSyntheses(params, desynthLevels);
        return solveWithLanes(homeNode, resale, craft, desynth, params);
    }

    /**
     * Run the solver over all lanes and attach the craft/desynth
     * sections. Both attachments share one consumption ledger per book
     * quality, so two picks shopping the same cheapest listings pay the
     * real (deeper) prices instead of both assuming the front of the
     * book: desynth buys consume first (they sit on the route), then
     * each craft's bill of materials in stop order.
     */
    private Plan solveWithLanes(
            WorldNode homeNode,
            List<Candidate> resale,
            List<CraftSynthesis> craft,
            List<DesynthSynthesis> desynth,
            PlannerParams params) {
        var combined = new ArrayList<Candidate>(resale.size() + craft.size() + desynth.size());
        combined.addAll(resale);
        for (var synthesis : craft) combined.add(synthesis.candidate());
        for (var synthesis : desynth) combined.add(synthesis.candidate());
        var solved = PlannerEngine.plan(homeNode, combined, params);
        var ledgerNq = new HashMap<Integer, Integer>();
        var ledgerHq = new HashMap<Integer, Integer>();
        solved = withDesynthSection(solved, desynth, params, ledgerNq, ledgerHq);
        return withCraftSection(solved, craft, homeNode, params, ledgerNq);
    }

    /**
     * Re-shape the engine's CRAFT picks. Their "buy the product at
     * ingredient cost" pseudo-buys leave the stop list and come back as
     * {@link PlanCraft} entries; the bills of materials are folded into
     * the route as MATERIAL stop lines — the player shops for
     * ingredients and resale picks on the same trip. Worlds that only
     * hold materials become new stops at the tail of the route (the
     * planner is DC-scoped, so every added hop costs the same
     * {@code tDcSeconds} regardless of position); the extra hop seconds
     * are charged against the objective.
     */
    private Plan withCraftSection(
            Plan plan,
            List<CraftSynthesis> syntheses,
            WorldNode home,
            PlannerParams params,
            Map<Integer, Integer> ledgerNq) {
        if (syntheses.isEmpty()) return plan;
        Map<String, CraftSynthesis> byKey = new HashMap<>();
        for (var s : syntheses) byKey.put(s.candidate().uniqueKey(), s);

        var picked = new ArrayList<CraftSynthesis>();
        long deltaBuyCost = 0;
        int deltaQty = 0;
        int deltaSlots = 0;

        // 1. Pull the chosen CRAFT pseudo-buys out of the stops.
        var stops = new ArrayList<PlanStop>(plan.stops().size());
        for (var stop : plan.stops()) {
            var kept = new ArrayList<PlanBuy>(stop.buys().size());
            int qty = stop.totalQty();
            int slots = stop.totalSlots();
            long cost = stop.buyCost();
            for (var buy : stop.buys()) {
                CraftSynthesis synthesis = buy.action() == PlanAction.CRAFT ? byKey.get(buy.uniqueKey()) : null;
                if (synthesis == null) {
                    kept.add(buy);
                    continue;
                }
                picked.add(synthesis);
                qty -= buy.qty();
                slots -= buy.slots();
                cost -= buy.totalCost();
                deltaBuyCost -= buy.totalCost();
                deltaQty -= buy.qty();
                deltaSlots -= buy.slots();
            }
            if (kept.isEmpty() && !stop.buys().isEmpty()) continue; // craft-only stop drops
            stops.add(
                    kept.size() == stop.buys().size()
                            ? stop
                            : new PlanStop(
                                    stop.worldId(),
                                    stop.worldName(),
                                    stop.dataCenterId(),
                                    stop.dataCenterName(),
                                    stop.hopSecondsFromPrev(),
                                    qty,
                                    slots,
                                    cost,
                                    kept));
        }
        if (picked.isEmpty()) return plan;

        // 1b. Re-price each chosen craft against the shared ledger — the
        //     first craft shops the front of the book, later crafts (and
        //     anything a desynth pick already took) pay the deeper
        //     prices. The valuation is refreshed at the dearer cost so
        //     the profit shown matches what the run actually pays.
        var prefs = new UserPrefs(config.value().mbTaxFor(params.homeDataCenterId()), params.attentionFraction(), 30.0);
        var crafts = new ArrayList<PlanCraft>(picked.size());
        for (var s : picked) {
            var bom =
                    CraftBomBuilder.build(s.recipe(), s.chain(), s.book(), this::nameOf, WorldNames::nameOf, ledgerNq);
            var base = s.craft();
            int yield = Math.max(1, base.qty());
            int perProductCost = (int) Math.ceil(bom.materialsCost() / (double) yield);
            var v = ValueEngine.valueCraftMixed(
                            s.nqModel(),
                            s.hqModel(),
                            1.0,
                            yield,
                            perProductCost,
                            prefs,
                            config.value().craftSecondsPerUnit())
                    .orElse(base.valuation());
            crafts.add(new PlanCraft(
                    base.uniqueKey(),
                    base.itemId(),
                    base.itemName(),
                    yield,
                    base.craftClass(),
                    base.craftLevel(),
                    base.craftVerified(),
                    bom.materials(),
                    bom.intermediates(),
                    bom.materialsCost(),
                    v,
                    s.chain().depthCapped()));
        }

        // 2. Aggregate every craft's materials by (world, item).
        var qtyByWorldItem = new LinkedHashMap<Integer, LinkedHashMap<Integer, int[]>>();
        var costByWorldItem = new HashMap<Integer, Map<Integer, Long>>();
        var itemNameById = new HashMap<Integer, String>();
        var worldNameById = new HashMap<Integer, String>();
        for (var craft : crafts) {
            for (var m : craft.materials()) {
                qtyByWorldItem.computeIfAbsent(m.worldId(), w -> new LinkedHashMap<>())
                                .computeIfAbsent(m.itemId(), i -> new int[1])[0] +=
                        m.qty();
                costByWorldItem
                        .computeIfAbsent(m.worldId(), w -> new HashMap<>())
                        .merge(m.itemId(), m.totalCost(), Long::sum);
                itemNameById.putIfAbsent(m.itemId(), m.itemName());
                worldNameById.putIfAbsent(m.worldId(), m.worldName());
            }
        }

        // 3. Fold the material lines into their worlds' stops; worlds not
        //    on the route become new stops appended to the tail.
        for (var worldEntry : qtyByWorldItem.entrySet()) {
            int worldId = worldEntry.getKey();
            var lines = new ArrayList<PlanBuy>(worldEntry.getValue().size());
            int matQty = 0;
            int matSlots = 0;
            long matCost = 0;
            for (var itemEntry : worldEntry.getValue().entrySet()) {
                int itemId = itemEntry.getKey();
                int qty = itemEntry.getValue()[0];
                long cost = costByWorldItem.get(worldId).get(itemId);
                int stack = Math.max(1, itemCatalog.stackSize(itemId));
                int slots = (qty + stack - 1) / stack;
                lines.add(new PlanBuy(
                        "material:%d-%d".formatted(itemId, worldId),
                        itemId,
                        itemNameById.get(itemId),
                        false,
                        qty,
                        slots,
                        (int) Math.ceil(cost / (double) qty),
                        PlanAction.MATERIAL,
                        null,
                        null,
                        null,
                        true));
                matQty += qty;
                matSlots += slots;
                matCost += cost;
            }
            deltaBuyCost += matCost;
            deltaQty += matQty;
            deltaSlots += matSlots;

            int idx = -1;
            for (int i = 0; i < stops.size(); i++) {
                if (stops.get(i).worldId() == worldId) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                var stop = stops.get(idx);
                var buys = new ArrayList<>(stop.buys());
                buys.addAll(lines);
                stops.set(
                        idx,
                        new PlanStop(
                                stop.worldId(),
                                stop.worldName(),
                                stop.dataCenterId(),
                                stop.dataCenterName(),
                                stop.hopSecondsFromPrev(),
                                stop.totalQty() + matQty,
                                stop.totalSlots() + matSlots,
                                stop.buyCost() + matCost,
                                buys));
            } else {
                var world = Worlds.worldById(worldId);
                var dc = world == null ? null : world.dataCenter();
                stops.add(new PlanStop(
                        worldId,
                        worldNameById.get(worldId),
                        dc == null ? plan.homeDataCenterId() : dc.id(),
                        dc == null ? plan.homeDataCenterName() : dc.name(),
                        0, // hop chain is recomputed below
                        matQty,
                        matSlots,
                        matCost,
                        lines));
            }
        }

        // 4. Recompute the hop chain — dropped and appended stops change it.
        int oldStopsHop =
                plan.stops().stream().mapToInt(PlanStop::hopSecondsFromPrev).sum() + plan.hopSecondsToHome();
        var prev = home;
        int newStopsHop = 0;
        for (int i = 0; i < stops.size(); i++) {
            var stop = stops.get(i);
            var node = new WorldNode(
                    stop.worldId(), stop.worldName(), stop.dataCenterId(), stop.dataCenterName(), home.regionName());
            int hop = de.chojo.lolorito.planner.HopCoster.hopSeconds(prev, node, params);
            newStopsHop += hop;
            if (hop != stop.hopSecondsFromPrev()) {
                stops.set(
                        i,
                        new PlanStop(
                                stop.worldId(),
                                stop.worldName(),
                                stop.dataCenterId(),
                                stop.dataCenterName(),
                                hop,
                                stop.totalQty(),
                                stop.totalSlots(),
                                stop.buyCost(),
                                stop.buys()));
            }
            prev = node;
        }
        int toHome = de.chojo.lolorito.planner.HopCoster.hopSeconds(prev, home, params);
        int deltaHop = newStopsHop + toHome - oldStopsHop;

        return plan.withCraftSection(stops, crafts, deltaBuyCost, deltaQty, deltaSlots, deltaHop, toHome);
    }

    /**
     * Attach the DESYNTH section for the picks the solver chose. The buy
     * lines stay on their stops (real listings on real worlds), but each
     * pick consumes its book depth from the shared ledger and gets
     * re-priced when something already took the cheapest listings — the
     * stop line, its cost, and the valuation all move together.
     */
    private Plan withDesynthSection(
            Plan plan,
            List<DesynthSynthesis> syntheses,
            PlannerParams params,
            Map<Integer, Integer> ledgerNq,
            Map<Integer, Integer> ledgerHq) {
        if (syntheses.isEmpty()) return plan;
        Map<String, DesynthSynthesis> byKey = new HashMap<>();
        for (var s : syntheses) byKey.put(s.candidate().uniqueKey(), s);
        var prefs = new UserPrefs(config.value().mbTaxFor(params.homeDataCenterId()), params.attentionFraction(), 30.0);

        var picked = new ArrayList<PlanDesynth>();
        long deltaBuyCost = 0;
        var stops = new ArrayList<PlanStop>(plan.stops().size());
        for (var stop : plan.stops()) {
            List<PlanBuy> buys = null;
            long cost = stop.buyCost();
            for (int i = 0; i < stop.buys().size(); i++) {
                var buy = stop.buys().get(i);
                if (buy.action() != PlanAction.DESYNTH) continue;
                var synthesis = byKey.get(buy.uniqueKey());
                if (synthesis == null) continue;
                var d = synthesis.desynth();
                var ledger = d.hq() ? ledgerHq : ledgerNq;
                int skip = ledger.getOrDefault(d.itemId(), 0);
                Integer unit = de.chojo.lolorito.value.ListingBook.unitCostFor(synthesis.levels(), d.qty(), skip);
                ledger.merge(d.itemId(), d.qty(), Integer::sum);
                int price = unit == null ? d.buyPrice() : unit;
                if (price != d.buyPrice()) {
                    var v = ValueEngine.valueDesynth(price, d.qty(), synthesis.pricings(), prefs)
                            .orElse(d.valuation());
                    d = new PlanDesynth(
                            d.uniqueKey(),
                            d.itemId(),
                            d.itemName(),
                            d.hq(),
                            d.qty(),
                            price,
                            (long) d.qty() * price,
                            d.sourceWorldId(),
                            d.sourceWorldName(),
                            d.desynthClass(),
                            d.desynthLevel(),
                            d.desynthVerified(),
                            d.outputs(),
                            v);
                    long lineDelta = (long) (price - buy.buyPrice()) * buy.qty();
                    if (buys == null) buys = new ArrayList<>(stop.buys());
                    buys.set(
                            i,
                            new PlanBuy(
                                    buy.uniqueKey(),
                                    buy.itemId(),
                                    buy.itemName(),
                                    buy.hq(),
                                    buy.qty(),
                                    buy.slots(),
                                    price,
                                    buy.action(),
                                    v,
                                    buy.craftClass(),
                                    buy.craftLevel(),
                                    buy.craftVerified()));
                    cost += lineDelta;
                    deltaBuyCost += lineDelta;
                }
                picked.add(d);
            }
            stops.add(
                    buys == null
                            ? stop
                            : new PlanStop(
                                    stop.worldId(),
                                    stop.worldName(),
                                    stop.dataCenterId(),
                                    stop.dataCenterName(),
                                    stop.hopSecondsFromPrev(),
                                    stop.totalQty(),
                                    stop.totalSlots(),
                                    cost,
                                    buys));
        }
        if (picked.isEmpty()) return plan;
        return plan.withDesynthSection(stops, picked, deltaBuyCost);
    }

    /**
     * Recomputes the plan ignoring the cache.
     */
    public Plan planUncached(PlannerParams params, int refreshHours) {
        return computePlan(params, refreshHours);
    }

    /**
     * Mid-run replan. Fetches fresh candidates, subtracts the
     * already-spent budget and used inventory, drops worlds the player
     * has already visited, and re-runs the solver on the remainder. Never
     * cached — the point is to reflect drift, not to return the last
     * snapshot.
     */
    public Plan replan(PlannerParams params, int refreshHours, ReplanContext ctx) {
        return replan(params, refreshHours, ctx, null, null);
    }

    /**
     * Lane-aware replan: craft and desynth candidates are re-synthesised
     * on the remaining budget alongside the resale pool. Completed worlds
     * drop out on every lane — including crafts whose bill of materials
     * shops on a world the player already left (revisiting it mid-run is
     * exactly what the replan promised to avoid).
     */
    public Plan replan(
            PlannerParams params,
            int refreshHours,
            ReplanContext ctx,
            Map<String, Integer> craftLevels,
            Map<String, Integer> desynthLevels) {
        var homeWorld = Worlds.worldById(params.homeWorldId());
        if (homeWorld == null || homeWorld.dataCenter() == null) {
            throw new IllegalArgumentException("unknown home world: " + params.homeWorldId());
        }
        long remBudget = Math.max(1L, params.budget() - Math.max(0L, ctx.spentBudget()));
        // ctx.usedInventory is the caller's already-consumed slot count
        // (not raw units) — the SPA sends the sum of the completed stops'
        // totalSlots so we can subtract straight from the slot budget.
        int remInv = Math.max(1, params.inventorySlots() - Math.max(0, ctx.usedInventory()));
        var adjusted = new PlannerParams(
                params.homeWorldId(),
                params.homeDataCenterId(),
                remBudget,
                remInv,
                params.attentionBudgetHours(),
                params.attentionFraction(),
                params.hopWeightGilPerSecond(),
                params.maxWorlds(),
                params.candidateTopK(),
                params.tDcSeconds(),
                params.tRegionSeconds(),
                params.ilpMaxNodes(),
                params.retainerSlots(),
                params.retainerListingSlots(),
                params.retainerListingStackTarget(),
                params.retainerAttentionFraction(),
                params.retainerShelfHoursThreshold());
        var homeNode = homeNodeOf(homeWorld);
        var resale = loadCandidates(adjusted, refreshHours).stream()
                .filter(c -> !ctx.completedWorldIds().contains(c.sourceWorldId()))
                .toList();
        var craft = loadCraftSyntheses(adjusted, craftLevels).stream()
                .filter(s -> s.candidate().materialWorlds().stream()
                        .noneMatch(w -> ctx.completedWorldIds().contains(w.worldId())))
                .toList();
        var desynth = loadDesynthSyntheses(adjusted, desynthLevels).stream()
                .filter(s -> !ctx.completedWorldIds().contains(s.candidate().sourceWorldId()))
                .toList();
        return solveWithLanes(homeNode, resale, craft, desynth, adjusted);
    }

    public void invalidateCache() {
        cache.invalidateAll();
    }

    public ResponseCache<CacheKey, Plan> cache() {
        return cache;
    }

    private Plan computePlan(PlannerParams params, int refreshHours) {
        var homeWorld = Worlds.worldById(params.homeWorldId());
        if (homeWorld == null || homeWorld.dataCenter() == null) {
            throw new IllegalArgumentException("unknown home world: " + params.homeWorldId());
        }
        var homeNode = homeNodeOf(homeWorld);
        var candidates = loadCandidates(params, refreshHours);
        return PlannerEngine.plan(homeNode, candidates, params);
    }

    /**
     * Enumerate every recipe the caller can perform (per their stored
     * skill levels — class AND level, not just class), price the
     * ingredients on the home DC, run the recipe through
     * {@link CraftChainPlanner}, and emit one CRAFT candidate per recipe
     * whose expected net > 0.
     *
     * <p>Cheap by design: bounded by the seeded recipe count (in the
     * hundreds even after {@code refreshCatalog}), one home-model
     * lookup + one batched ingredient-price lookup per recipe. Skips
     * recipes with no home model (no signal on sell price), no
     * ingredient pricing (nothing to buy), or non-positive expected
     * profit.
     *
     * @param craftLevels null → no CRAFT synthesis. Empty → ungated
     *   fallback (explicit opt-in with no stored skills). Non-empty →
     *   recipes with {@code level > craftLevels[class]} are dropped and
     *   sub-crafts gate the same way.
     */
    public List<Candidate> loadCraftCandidates(PlannerParams params, Map<String, Integer> craftLevels) {
        return loadCraftSyntheses(params, craftLevels).stream()
                .map(CraftSynthesis::candidate)
                .toList();
    }

    /**
     * A CRAFT candidate for the solver plus the {@link PlanCraft} the SPA
     * renders when it's picked — and everything needed to re-price the
     * bill of materials against the shared book at plan assembly.
     */
    public record CraftSynthesis(
            Candidate candidate,
            PlanCraft craft,
            Recipes.Recipe recipe,
            CraftChainPlanner.ChainPlan chain,
            Map<Integer, List<ItemDetail.PriceLevel>> book,
            MarketModel nqModel,
            MarketModel hqModel) {}

    /** Candidate synthesis that also keeps the bill of materials behind each candidate's cost. */
    public List<CraftSynthesis> loadCraftSyntheses(PlannerParams params, Map<String, Integer> craftLevels) {
        if (craftLevels == null) return List.of();
        var homeWorld = Worlds.worldById(params.homeWorldId());
        if (homeWorld == null || homeWorld.dataCenter() == null) return List.of();
        var prefs = new UserPrefs(config.value().mbTaxFor(params.homeDataCenterId()), params.attentionFraction(), 30.0);
        var gate = CraftChainPlanner.levelGate(craftLevels);
        boolean verified = !craftLevels.isEmpty();

        var out = new ArrayList<CraftSynthesis>();
        for (var head : recipes.allHeads()) {
            if (!gate.canCraft(head.craftClass(), head.level())) continue;

            // HQ is where most crafting profit lives — value the craft as a
            // mixture of both quality outcomes instead of scanning NQ only.
            MarketModel nqModel = itemDetail
                    .homeModel(params.homeWorldId(), head.productItemId(), false)
                    .orElse(null);
            MarketModel hqModel = itemDetail
                    .homeModel(params.homeWorldId(), head.productItemId(), true)
                    .orElse(null);
            if (nqModel == null && hqModel == null) continue;

            var recipe = recipes.find(head.id()).orElse(null);
            if (recipe == null) continue;

            // Batched cheap-book lookup across the home DC — one round-trip
            // per recipe, depth-aware: unit cost walks the price steps for
            // the quantity the recipe needs instead of trusting a possibly
            // one-unit min-price row. The planner stays DC-scoped (hop
            // weights only make sense inside one DC).
            List<Integer> ingredientIds = collectAllIngredients(recipe);
            var needed = ItemDetailService.neededQuantities(recipe);
            var book = itemDetail.cheapBook(
                    params.homeDataCenterId(),
                    null,
                    ingredientIds,
                    false,
                    config.value().listingFreshnessHours(),
                    BOOK_LEVELS);
            java.util.function.IntFunction<Integer> cost =
                    id -> de.chojo.lolorito.value.ListingBook.unitCostFor(book.get(id), needed.getOrDefault(id, 1));

            var chain = CraftChainPlanner.plan(recipe, cost, config.planner().craftChainMaxDepth(), gate);
            if (chain.perProductCost() == null) continue;
            int perProductCost = (int) Math.ceil(chain.perProductCost());
            if (perProductCost <= 0) continue;

            // Value the whole craft run — qty must match the candidate's
            // qty (the yield) or the ILP weighs a multi-yield recipe's
            // full material cost against a single unit's EV.
            int yield = Math.max(1, head.yield());
            // Crafting always assumes the crafter meets the HQ thresholds:
            // certain-HQ wherever an HQ model exists; the NQ branch only
            // carries products that cannot be HQ at all.
            var v = ValueEngine.valueCraftMixed(
                            nqModel,
                            hqModel,
                            1.0,
                            yield,
                            perProductCost,
                            prefs,
                            config.value().craftSecondsPerUnit())
                    .orElse(null);
            if (v == null || v.evGross() <= 0) continue;

            var bom = CraftBomBuilder.build(recipe, chain, book, this::nameOf, WorldNames::nameOf);
            // Every world the shopping list touches beyond home — the
            // solver charges their hops when this candidate is picked.
            var materialWorlds = new LinkedHashMap<Integer, WorldNode>();
            for (var m : bom.materials()) {
                if (m.worldId() == homeWorld.id()) continue;
                materialWorlds.computeIfAbsent(m.worldId(), id -> {
                    var w = Worlds.worldById(id);
                    var dc = w == null ? null : w.dataCenter();
                    return new WorldNode(
                            id,
                            m.worldName(),
                            dc == null ? homeWorld.dataCenter().id() : dc.id(),
                            dc == null ? homeWorld.dataCenter().name() : dc.name(),
                            homeWorld.dataCenter().region().name());
                });
            }
            var candidate = new Candidate(
                    "craft:" + head.id(),
                    head.productItemId(),
                    nameOf(head.productItemId()),
                    false,
                    homeWorld.id(),
                    homeWorld.name(),
                    homeWorld.dataCenter().id(),
                    homeWorld.dataCenter().name(),
                    homeWorld.dataCenter().region().name(),
                    yield,
                    perProductCost,
                    itemCatalog.stackSize(head.productItemId()),
                    PlanAction.CRAFT,
                    v,
                    head.craftClass(),
                    head.level(),
                    verified,
                    List.copyOf(materialWorlds.values()));
            out.add(new CraftSynthesis(
                    candidate,
                    new PlanCraft(
                            candidate.uniqueKey(),
                            head.productItemId(),
                            candidate.itemName(),
                            yield,
                            head.craftClass(),
                            head.level(),
                            verified,
                            bom.materials(),
                            bom.intermediates(),
                            bom.materialsCost(),
                            v,
                            chain.depthCapped()),
                    recipe,
                    chain,
                    book,
                    nqModel,
                    hqModel));
        }
        return out;
    }

    /**
     * A DESYNTH candidate for the solver plus the {@link PlanDesynth} the
     * SPA renders when it's picked — with the source book levels and
     * component pricings needed to re-price against the shared ledger.
     */
    public record DesynthSynthesis(
            Candidate candidate,
            PlanDesynth desynth,
            List<ItemDetail.PriceLevel> levels,
            List<ComponentPricing> pricings) {}

    /** Sources evaluated per plan — same walk cap the desynth explorer uses. */
    private static final int MAX_DESYNTH_SOURCES = 500;

    /**
     * Enumerate every desynth source the caller can perform, price the
     * source on the home DC (per world, via the cheap book — the buy is a
     * real stop line, so it needs a real world), value the components at
     * home with {@link ValueEngine#valueDesynth}, and emit one DESYNTH
     * candidate per profitable source. Quantity starts at the cheapest
     * level's depth and is capped by the caller's attention budget — the
     * components' clear time scales linearly with the units desynthed.
     *
     * @param desynthLevels null → no DESYNTH synthesis. Empty → ungated
     *   fallback (explicit opt-in with no stored skills, mirroring the
     *   craft scan). Non-empty → sources gate on class and level; rows
     *   with unknown class/level pass (can't rank the unknown).
     */
    public List<DesynthSynthesis> loadDesynthSyntheses(PlannerParams params, Map<String, Integer> desynthLevels) {
        if (desynthLevels == null) return List.of();
        var homeWorld = Worlds.worldById(params.homeWorldId());
        if (homeWorld == null || homeWorld.dataCenter() == null) return List.of();
        var prefs = new UserPrefs(config.value().mbTaxFor(params.homeDataCenterId()), params.attentionFraction(), 30.0);
        boolean verified = !desynthLevels.isEmpty();

        var eligible = new ArrayList<DesynthResults.Source>();
        for (var src : desynthResults.allSources()) {
            if (!desynthGatePasses(src, desynthLevels)) continue;
            if (eligible.size() >= MAX_DESYNTH_SOURCES) break;
            eligible.add(src);
        }
        if (eligible.isEmpty()) return List.of();

        int freshHours = config.value().listingFreshnessHours();
        var sourceIds = eligible.stream().map(DesynthResults.Source::itemId).toList();
        // Gear sources are frequently listed HQ-only — probe both
        // qualities and buy whichever board is cheaper. Components come
        // out the same either way.
        var bookNq = itemDetail.cheapBook(params.homeDataCenterId(), null, sourceIds, false, freshHours, BOOK_LEVELS);
        var bookHq = itemDetail.cheapBook(params.homeDataCenterId(), null, sourceIds, true, freshHours, BOOK_LEVELS);

        var componentsBySource = new HashMap<Integer, List<DesynthResults.Component>>();
        var componentIds = new java.util.HashSet<Integer>();
        for (var src : eligible) {
            if (!bookNq.containsKey(src.itemId()) && !bookHq.containsKey(src.itemId())) continue;
            var components = desynthResults.findBySource(src.itemId());
            if (components.isEmpty()) continue;
            componentsBySource.put(src.itemId(), components);
            for (var c : components) componentIds.add(c.componentItemId());
        }
        var models = marketModels.findAll(params.homeWorldId(), componentIds, false);

        var out = new ArrayList<DesynthSynthesis>();
        for (var src : eligible) {
            var components = componentsBySource.get(src.itemId());
            if (components == null) continue;
            var nqLevels = bookNq.get(src.itemId());
            var hqLevels = bookHq.get(src.itemId());
            boolean hqSource = cheaperFirstLevelIsHq(nqLevels, hqLevels);
            var levels = hqSource ? hqLevels : nqLevels;
            var cheapest = levels.getFirst();
            if (cheapest.unitPrice() <= 0) continue;

            var pricings = new ArrayList<ComponentPricing>(components.size());
            var outputs = new ArrayList<PlanDesynthOutput>(components.size());
            for (var c : components) {
                var model = models.get(c.componentItemId());
                pricings.add(new ComponentPricing(c.componentItemId(), c.avgQty(), model, null));
                Double unitNet = model != null && model.sufficient()
                        ? model.price().medianPrice() * (1.0 - prefs.mbTax())
                        : null;
                outputs.add(
                        new PlanDesynthOutput(c.componentItemId(), nameOf(c.componentItemId()), c.avgQty(), unitNet));
            }

            var perUnit = ValueEngine.valueDesynth(cheapest.unitPrice(), 1, pricings, prefs)
                    .orElse(null);
            if (perUnit == null || perUnit.evGross() <= 0) continue;
            int qty = desynthQtyCap(perUnit, Math.max(1, cheapest.quantity()), params);
            var v = qty == 1
                    ? perUnit
                    : ValueEngine.valueDesynth(cheapest.unitPrice(), qty, pricings, prefs)
                            .orElse(null);
            if (v == null || v.evGross() <= 0) continue;

            var sourceWorld = Worlds.worldById(cheapest.worldId());
            if (sourceWorld == null || sourceWorld.dataCenter() == null) continue;

            String key = "desynth:%d-%d-%s".formatted(src.itemId(), cheapest.worldId(), hqSource ? "hq" : "nq");
            String name = nameOf(src.itemId());
            var candidate = new Candidate(
                    key,
                    src.itemId(),
                    name,
                    hqSource,
                    sourceWorld.id(),
                    sourceWorld.name(),
                    sourceWorld.dataCenter().id(),
                    sourceWorld.dataCenter().name(),
                    sourceWorld.dataCenter().region().name(),
                    qty,
                    cheapest.unitPrice(),
                    itemCatalog.stackSize(src.itemId()),
                    PlanAction.DESYNTH,
                    v,
                    src.desynthClass(),
                    src.desynthLevel(),
                    verified,
                    List.of());
            out.add(new DesynthSynthesis(
                    candidate,
                    new PlanDesynth(
                            key,
                            src.itemId(),
                            name,
                            hqSource,
                            qty,
                            cheapest.unitPrice(),
                            (long) qty * cheapest.unitPrice(),
                            sourceWorld.id(),
                            sourceWorld.name(),
                            src.desynthClass(),
                            src.desynthLevel(),
                            verified,
                            List.copyOf(outputs),
                            v),
                    List.copyOf(levels),
                    List.copyOf(pricings)));
        }
        return out;
    }

    /** True when the HQ board's cheapest listing undercuts (or is the only) NQ one. */
    private static boolean cheaperFirstLevelIsHq(
            List<ItemDetail.PriceLevel> nqLevels, List<ItemDetail.PriceLevel> hqLevels) {
        boolean hasNq = nqLevels != null && !nqLevels.isEmpty();
        boolean hasHq = hqLevels != null && !hqLevels.isEmpty();
        if (!hasHq) return false;
        if (!hasNq) return true;
        return hqLevels.getFirst().unitPrice() < nqLevels.getFirst().unitPrice();
    }

    /**
     * How many sources to buy at the cheapest level: bounded by the level's
     * depth and by the attention budget — each desynthed unit adds its
     * components' clear time to the shelf clock.
     */
    private static int desynthQtyCap(de.chojo.lolorito.value.Valuation perUnit, int available, PlannerParams params) {
        double attentionPerUnit = perUnit.expectedTimeOnShelfHours() * params.attentionFraction();
        if (!Double.isFinite(attentionPerUnit) || attentionPerUnit <= 0.0) return 1;
        int cap = (int) Math.floor(params.attentionBudgetHours() / attentionPerUnit);
        return Math.max(1, Math.min(available, cap));
    }

    /**
     * Mirrors the desynth explorer's gate: no stored skills → everything
     * passes (the flag was the consent); rows with unknown class or level
     * pass rather than hide.
     */
    private static boolean desynthGatePasses(DesynthResults.Source src, Map<String, Integer> desynthLevels) {
        if (desynthLevels.isEmpty()) return true;
        if (src.desynthClass() == null || src.desynthLevel() == null) return true;
        Integer callerLevel = desynthLevels.get(src.desynthClass());
        return callerLevel != null && callerLevel >= src.desynthLevel();
    }

    private static List<Integer> collectAllIngredients(Recipes.Recipe root) {
        var seen = new java.util.LinkedHashSet<Integer>();
        collectIngredients(root, seen);
        return List.copyOf(seen);
    }

    private static void collectIngredients(Recipes.Recipe r, Set<Integer> seen) {
        for (var ing : r.ingredients()) {
            seen.add(ing.itemId());
            var sub = ing.recipeToCraftIt();
            if (sub != null) collectIngredients(sub, seen);
        }
    }

    private WorldNode homeNodeOf(World homeWorld) {
        return new WorldNode(
                homeWorld.id(),
                homeWorld.name(),
                homeWorld.dataCenter().id(),
                homeWorld.dataCenter().name(),
                homeWorld.dataCenter().region().name());
    }

    /**
     * Fetch and score the coarse candidate pool for {@code params}. Shared
     * by {@link #plan}, {@link #planUncached}, and {@link #replan} so the
     * three paths agree on what "the current fresh candidates" means.
     */
    private List<Candidate> loadCandidates(PlannerParams params, int refreshHours) {
        int coarseCap = Math.max(params.candidateTopK() * 4, 500);
        // Planner is DC-scoped by construction — hop weights only make
        // sense inside one DC — so we pass DATA_CENTER regardless of the
        // user's persisted offer-explorer scope.
        var raw = offers.candidates(
                params.homeWorldId(),
                params.homeDataCenterId(),
                "",
                de.chojo.lolorito.entity.OfferFilterTarget.DATA_CENTER,
                Math.max(1, refreshHours),
                coarseCap);
        var prefs = new UserPrefs(config.value().mbTaxFor(params.homeDataCenterId()), params.attentionFraction(), 30.0);
        var candidates = new ArrayList<Candidate>(raw.size());
        for (Offers.Candidate r : raw) {
            int qty = capQuantityByExpectedVolume(r, params);
            if (qty <= 0) continue;
            var v = ValueEngine.value(r.model(), r.buyPrice(), qty, prefs).orElse(null);
            if (v == null || v.evGross() <= 0) continue;
            var source = Worlds.worldById(r.sourceWorldId());
            if (source == null || source.dataCenter() == null) continue;
            candidates.add(Candidate.simple(
                    candidateKey(r, source),
                    r.itemId(),
                    nameOf(r.itemId()),
                    r.hq(),
                    source.id(),
                    source.name(),
                    source.dataCenter().id(),
                    source.dataCenter().name(),
                    source.dataCenter().region().name(),
                    qty,
                    r.buyPrice(),
                    itemCatalog.stackSize(r.itemId()),
                    PlanAction.RESALE,
                    v));
        }
        return candidates;
    }

    private String nameOf(int itemId) {
        if (itemNames == null) return String.valueOf(itemId);
        var name = itemNames.fromId(itemId);
        return name == null ? String.valueOf(itemId) : name.get(Language.ENGLISH);
    }

    /**
     * Cap the listing quantity by the expected sales volume in the caller's
     * attention window. A candidate whose listing sits
     * at {@code buyPrice} with expected sale rate λ (units/hour at median
     * price) can realistically clear ~ λ × attentionHours units before the
     * player's attention runs out. Buying more than that guarantees a tail
     * of unsold stock, so we cap here — before the ILP considers the
     * candidate — and let {@link ValueEngine#value} re-score at the smaller
     * quantity. Returns 0 when no sane cap can be computed.
     */
    private static int capQuantityByExpectedVolume(Offers.Candidate r, PlannerParams params) {
        int listed = Math.max(1, r.quantity());
        var rate = r.model().saleRate();
        double perHour = Math.max(0.0, rate.lambdaP100());
        if (perHour <= 0.0) return listed;
        double expected = perHour * Math.max(0.5, params.attentionBudgetHours());
        int cap = (int) Math.max(1, Math.floor(expected));
        return Math.min(listed, cap);
    }

    /**
     * Cache key — the full solver input.
     */
    public record CacheKey(PlannerParams params, int refreshHours) {}

    /**
     * Inputs the SPA already knows when it clicks "I finished world X" —
     * which worlds are done, how much of the budget and inventory the
     * player has already burned.
     */
    public record ReplanContext(Set<Integer> completedWorldIds, long spentBudget, int usedInventory) {}

    /**
     * POST body for {@code /api/v1/plan/replan} — the plan params plus what's been done so far.
     */
    public record ReplanRequest(
            Integer homeWorld,
            Integer refreshHours,
            Long budget,
            Integer inventorySlots,
            Double attentionBudgetHours,
            Double attentionFraction,
            Integer hopWeightGilPerSecond,
            Integer maxWorlds,
            Integer candidateTopK,
            Integer retainerSlots,
            Integer retainerListingSlots,
            Integer retainerListingStackTarget,
            Double retainerAttentionFraction,
            Double retainerShelfHoursThreshold,
            Set<Integer> completedWorldIds,
            Long spentBudget,
            Integer usedInventory,
            /** Same lane flags as {@link PlanRequest} — a replan keeps the original run's lanes. */
            Boolean allowCrafts,
            Boolean allowDesynth) {}

    /**
     * POST body shape — every field optional; unspecified fields fall back to config defaults.
     */
    public record PlanRequest(
            Integer homeWorld,
            Integer refreshHours,
            Long budget,
            Integer inventorySlots,
            Double attentionBudgetHours,
            Double attentionFraction,
            Integer hopWeightGilPerSecond,
            Integer maxWorlds,
            Integer candidateTopK,
            Integer retainerSlots,
            Integer retainerListingSlots,
            Integer retainerListingStackTarget,
            Double retainerAttentionFraction,
            Double retainerShelfHoursThreshold,
            /**
             * When true, the planner is allowed to emit CRAFT candidates
             * alongside RESALE picks — gated by the caller's stored
             * per-class crafter levels. When false or absent, RESALE-only
             * (today's behaviour). Backend candidate synthesis for CRAFT
             * is a separate follow-up; the field ships now so the SPA has
             * a stable request shape.
             */
            Boolean allowCrafts,
            /**
             * Same shape for the DESYNTH lane — gated by the caller's
             * stored per-class desynth levels; with none stored, the
             * explicit flag consents to the ungated scan.
             */
            Boolean allowDesynth) {}
}
