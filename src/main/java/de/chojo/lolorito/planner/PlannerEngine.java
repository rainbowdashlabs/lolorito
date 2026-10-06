/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

import de.chojo.lolorito.planner.ilp.KnapsackItem;
import de.chojo.lolorito.planner.ilp.KnapsackSolver;
import de.chojo.lolorito.planner.ilp.Solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The joint route-basket solver. Pure — {@code candidates} arrive
 * prescored, and everything below the API is math.
 *
 * <p>Two layers, sized for the search space we actually have (~250
 * candidate items × ~10 worlds × 3 actions):
 *
 * <ol>
 *   <li><b>Coarse candidate generation</b> — sort by EV/hour descending, keep
 *       the top {@link PlannerParams#candidateTopK()}.</li>
 *   <li><b>Beam search over world subsets, refined by ILP.</b> Enumerate
 *       subsets of size ≤ {@link PlannerParams#maxWorlds()}. For each subset
 *       we run {@link KnapsackSolver} over the candidates whose source world
 *       is in the subset, then subtract the hop cost.</li>
 * </ol>
 *
 * <p>The winning subset gets its stops ordered by {@link HopOrderer}. That's
 * the plan we return.
 */
public final class PlannerEngine {

    private static final Solver DEFAULT_SOLVER = new KnapsackSolver();

    private PlannerEngine() {}

    /**
     * Compute a plan using the default {@link KnapsackSolver}.
     */
    public static Plan plan(WorldNode homeWorld, List<Candidate> candidates, PlannerParams params) {
        return plan(homeWorld, candidates, params, DEFAULT_SOLVER);
    }

    /**
     * Compute a plan using a caller-supplied {@link Solver} — the escape
     * hatch. Any implementation that respects the {@link
     * Solver} contract (three capacity caps, single-source per group)
     * slots in here without touching the beam or the hop coster.
     */
    public static Plan plan(WorldNode homeWorld, List<Candidate> candidates, PlannerParams params, Solver solver) {
        if (candidates.isEmpty()) {
            return Plan.empty(
                    homeWorld.worldId(), homeWorld.worldName(), homeWorld.dataCenterId(), homeWorld.dataCenterName());
        }

        // 0. Retainer-overnight partition. Slow movers whose
        //    retainer EV/hr beats their active EV/hr get parked with
        //    retainers first; the hop solver runs on the leftover budget
        //    and inventory.
        RetainerPartition partition = partitionRetainerPicks(candidates, params);
        long hopBudget = Math.max(0L, params.budget() - partition.spend());
        int hopInventory = Math.max(0, params.inventorySlots() - partition.slots());
        PlannerParams hopParams = withReducedResources(params, hopBudget, hopInventory);

        // 1. Coarse pass — take the top-K by EV/hour, drop rows the engine can't fit alone.
        var scored = new ArrayList<Candidate>();
        for (Candidate c : partition.hopCandidates()) {
            if (c.valuation() == null) continue;
            if (c.valuation().evGross() <= 0) continue;
            if (c.totalCost() > hopParams.budget()) continue;
            if (c.slotsConsumed() > hopParams.inventorySlots()) continue;
            double att = c.attentionHours(hopParams.attentionFraction());
            if (att > hopParams.attentionBudgetHours()) continue;
            scored.add(c);
        }
        scored.sort(Comparator.comparingDouble((Candidate c) -> c.valuation().evPerHour())
                .reversed());
        if (scored.size() > hopParams.candidateTopK())
            scored = new ArrayList<>(scored.subList(0, hopParams.candidateTopK()));
        if (scored.isEmpty()) {
            return withRetainer(
                    Plan.empty(
                            homeWorld.worldId(),
                            homeWorld.worldName(),
                            homeWorld.dataCenterId(),
                            homeWorld.dataCenterName()),
                    partition,
                    hopParams,
                    homeWorld);
        }

        // 2. World-subset beam. Universe = distinct source worlds in the top-K.
        var worldsById = new LinkedHashMap<Integer, WorldNode>();
        var candidatesByWorld = new HashMap<Integer, List<Candidate>>();
        for (Candidate c : scored) {
            worldsById.computeIfAbsent(
                    c.sourceWorldId(),
                    id -> new WorldNode(
                            c.sourceWorldId(),
                            c.sourceWorldName(),
                            c.dataCenterId(),
                            c.dataCenterName(),
                            c.regionName()));
            candidatesByWorld
                    .computeIfAbsent(c.sourceWorldId(), id -> new ArrayList<>())
                    .add(c);
        }
        var worldIds = new ArrayList<>(worldsById.keySet());
        int worldCount = worldIds.size();
        int maxWorlds = Math.min(hopParams.maxWorlds(), worldCount);

        Best best = new Best();
        int[] subsetsConsidered = {0};
        var subset = new int[maxWorlds];
        for (int size = 1; size <= maxWorlds; size++) {
            enumerateSubsets(worldIds, subset, 0, 0, size, ids -> {
                subsetsConsidered[0]++;
                evaluateSubset(homeWorld, ids, worldsById, candidatesByWorld, hopParams, solver, best);
            });
        }

        if (best.subset == null) {
            return withRetainer(
                    Plan.empty(
                            homeWorld.worldId(),
                            homeWorld.worldName(),
                            homeWorld.dataCenterId(),
                            homeWorld.dataCenterName()),
                    partition,
                    hopParams,
                    homeWorld);
        }

        return withRetainer(
                assemble(homeWorld, best, worldsById, hopParams, scored.size(), subsetsConsidered[0]),
                partition,
                hopParams,
                homeWorld);
    }

    // --- Subset enumeration ------------------------------------------------

    private static void enumerateSubsets(
            List<Integer> worldIds, int[] buffer, int start, int depth, int size, SubsetCallback cb) {
        if (depth == size) {
            var ids = new ArrayList<Integer>(size);
            for (int i = 0; i < size; i++) ids.add(buffer[i]);
            cb.accept(ids);
            return;
        }
        int remaining = size - depth;
        for (int i = start; i <= worldIds.size() - remaining; i++) {
            buffer[depth] = worldIds.get(i);
            enumerateSubsets(worldIds, buffer, i + 1, depth + 1, size, cb);
        }
    }

    private static void evaluateSubset(
            WorldNode home,
            List<Integer> subsetIds,
            Map<Integer, WorldNode> worldsById,
            Map<Integer, List<Candidate>> candidatesByWorld,
            PlannerParams params,
            Solver solver,
            Best best) {
        var pool = new ArrayList<Candidate>();
        for (int wid : subsetIds) pool.addAll(candidatesByWorld.get(wid));
        var items = new ArrayList<KnapsackItem>(pool.size());
        for (Candidate c : pool) {
            double att = c.attentionHours(params.attentionFraction());
            if (!Double.isFinite(att)) continue;
            // KnapsackItem.qty is the inventory footprint, which is slots
            // (each holds up to `stackSize` units), NOT raw unit count.
            items.add(new KnapsackItem(c, c.itemId(), c.valuation().evGross(), c.totalCost(), c.slotsConsumed(), att));
        }
        var res = solver.solve(
                items, params.budget(), params.inventorySlots(), params.attentionBudgetHours(), params.ilpMaxNodes());
        if (res.picks().isEmpty()) return;
        var chosen = new ArrayList<Candidate>();
        var chosenWorlds = new HashSet<Integer>();
        for (KnapsackItem it : res.picks()) {
            var c = (Candidate) it.id();
            chosen.add(c);
            chosenWorlds.add(c.sourceWorldId());
        }
        // Subtract hop cost — we still need to know the true stop set (chosenWorlds), not the enumerated subset.
        var stopNodes = new ArrayList<WorldNode>();
        for (int wid : chosenWorlds) stopNodes.add(worldsById.get(wid));
        var spend = spendByWorld(chosen);
        var ordering = HopOrderer.order(home, stopNodes, spend, params);
        // long math: cross-region hops arrive here as Integer.MAX_VALUE from HopCoster.
        long totalHopSeconds = (long) ordering.totalHopSeconds() + (long) ordering.hopSecondsToHome();
        totalHopSeconds += materialHopSeconds(home, chosen, chosenWorlds, params);
        double objective = res.value() - (double) totalHopSeconds * params.hopWeightGilPerSecond();
        if (objective > best.objective) {
            best.objective = objective;
            best.value = res.value();
            best.subset = subsetIds;
            best.picks = chosen;
            best.ordering = ordering;
        }
    }

    // --- Per-subset evaluation --------------------------------------------

    /**
     * Hop seconds the picked candidates' bills of materials add on top of
     * the route: every material world not already visited costs one hop
     * to reach, plus one return leg when the route had no stops at all
     * (a craft-only plan still travels out and back). This is what makes
     * a craft with off-route materials pay for its shopping trip inside
     * the solver instead of looking free until plan assembly.
     */
    private static long materialHopSeconds(
            WorldNode home, List<Candidate> chosen, Set<Integer> visitedWorlds, PlannerParams params) {
        var counted = new HashSet<>(visitedWorlds);
        counted.add(home.worldId());
        boolean emptyTour = visitedWorlds.stream().allMatch(id -> id == home.worldId());
        long extra = 0;
        boolean added = false;
        for (Candidate c : chosen) {
            for (WorldNode w : c.materialWorlds()) {
                if (!counted.add(w.worldId())) continue;
                extra += HopCoster.hopSeconds(home, w, params);
                added = true;
            }
        }
        if (added && emptyTour) extra += params.tDcSeconds();
        return extra;
    }

    private static Map<Integer, Long> spendByWorld(List<Candidate> chosen) {
        var out = new HashMap<Integer, Long>();
        for (Candidate c : chosen) out.merge(c.sourceWorldId(), c.totalCost(), Long::sum);
        return out;
    }

    private static Plan assemble(
            WorldNode home,
            Best best,
            Map<Integer, WorldNode> worldsById,
            PlannerParams params,
            int candidatesConsidered,
            int subsetsConsidered) {
        var byWorld = new LinkedHashMap<Integer, List<PlanBuy>>();
        var spendByWorld = new HashMap<Integer, Long>();
        var qtyByWorld = new HashMap<Integer, Integer>();
        var slotsByWorld = new HashMap<Integer, Integer>();
        long totalEv = 0;
        long totalCost = 0;
        int totalQty = 0;
        int totalSlots = 0;
        double totalAttention = 0.0;
        for (Candidate c : best.picks) {
            int slots = c.slotsConsumed();
            byWorld.computeIfAbsent(c.sourceWorldId(), id -> new ArrayList<>())
                    .add(new PlanBuy(
                            c.uniqueKey(),
                            c.itemId(),
                            c.itemName(),
                            c.hq(),
                            c.qty(),
                            slots,
                            c.buyPrice(),
                            c.action(),
                            c.valuation(),
                            c.craftClass(),
                            c.craftLevel(),
                            c.craftVerified()));
            spendByWorld.merge(c.sourceWorldId(), c.totalCost(), Long::sum);
            qtyByWorld.merge(c.sourceWorldId(), c.qty(), Integer::sum);
            slotsByWorld.merge(c.sourceWorldId(), slots, Integer::sum);
            totalEv += Math.round(c.valuation().evGross());
            totalCost += c.totalCost();
            totalQty += c.qty();
            totalSlots += slots;
            totalAttention += c.attentionHours(params.attentionFraction());
        }
        var stops = new ArrayList<PlanStop>();
        for (int i = 0; i < best.ordering.ordered().size(); i++) {
            var node = best.ordering.ordered().get(i);
            var buys = byWorld.get(node.worldId());
            buys.sort(Comparator.comparingLong(PlanBuy::totalCost).reversed());
            stops.add(new PlanStop(
                    node.worldId(),
                    node.worldName(),
                    node.dataCenterId(),
                    node.dataCenterName(),
                    best.ordering.hopSecondsFromPrev()[i],
                    qtyByWorld.getOrDefault(node.worldId(), 0),
                    slotsByWorld.getOrDefault(node.worldId(), 0),
                    spendByWorld.getOrDefault(node.worldId(), 0L),
                    buys));
        }
        int hopSecondsToHome = best.ordering.hopSecondsToHome();
        int hopSecondsTotal = best.ordering.totalHopSeconds() + hopSecondsToHome;
        return new Plan(
                home.worldId(),
                home.worldName(),
                home.dataCenterId(),
                home.dataCenterName(),
                best.objective,
                totalEv,
                totalCost,
                totalQty,
                totalSlots,
                totalAttention,
                hopSecondsTotal,
                hopSecondsToHome,
                candidatesConsidered,
                subsetsConsidered,
                stops,
                List.of(), // retainerBasket — filled by withRetainer
                0L,
                0,
                0,
                0.0,
                List.of(), // crafts — filled by PlannerService.withCraftSection
                0L,
                0.0,
                List.of(), // desynths — filled by PlannerService.withDesynthSection
                0L,
                0.0,
                0.0,
                0.0);
    }

    // --- Retainer partition ------------------------------------------------

    /**
     * Greedy retainer-slot allocation. For each candidate we compare the
     * player-attention EV/hr against the retainer EV/hr (same valuation,
     * lower attention fraction). If retainer wins <em>and</em> the expected
     * shelf time exceeds the configured threshold, the candidate is
     * eligible. We sort eligibles by retainer EV/hr, then take from the top
     * until the retainer-slot count, the budget, or the inventory is
     * exhausted.
     */
    /**
     * Fraction of the caller's budget + inventory the retainer partition
     * is allowed to consume. The remainder is reserved for the hop plan.
     * Without this the greedy loop would happily allocate everything to
     * high-EV retainer picks and starve the hop planner — the SPA then
     * shows "no plan today" even though the retainer basket is full.
     */
    private static final double RETAINER_BUDGET_FRACTION = 0.4;

    /** One-shot run share charged per retainer pick, matching the item-detail and planner services. */
    private static final double RETAINER_RUN_SHARE_SECONDS = 30.0;

    private static RetainerPartition partitionRetainerPicks(List<Candidate> candidates, PlannerParams params) {
        if (params.retainerSlots() <= 0) return RetainerPartition.emptyFor(candidates);

        var eligible = new ArrayList<Candidate>();
        var retainerEv = new HashMap<Candidate, Double>();
        for (Candidate c : candidates) {
            // Only resale picks can be parked: a retainer resells the item
            // as-is, while CRAFT/DESYNTH valuations assume the player
            // processes it at home first.
            if (c.action() != PlanAction.RESALE) continue;
            if (c.valuation() == null || c.valuation().evGross() <= 0) continue;
            if (c.valuation().expectedTimeOnShelfHours() < params.retainerShelfHoursThreshold()) continue;
            double rEv = c.valuation().evPerHourAt(params.retainerAttentionFraction(), RETAINER_RUN_SHARE_SECONDS);
            if (rEv <= c.valuation().evPerHour()) continue; // active attention already wins
            eligible.add(c);
            retainerEv.put(c, rEv);
        }
        eligible.sort(Comparator.comparingDouble(retainerEv::get).reversed());

        var picked = new ArrayList<Candidate>();
        var pickedRetainerEv = new HashMap<Candidate, Double>();
        long retainerBudget = Math.max(0L, (long) Math.floor(params.budget() * RETAINER_BUDGET_FRACTION));
        long remBudget = retainerBudget;
        // Two-slot model:
        //  - carry (params.inventorySlots): what fits in the backpack on
        //    the trip home. Each pick is one stacked batch → one slot.
        //  - retainer listings (params.retainerListingSlots): what fits
        //    across all retainers as separate sell listings. A pick with
        //    N units is expected to be split into ceil(N / target)
        //    listings, so a stackable batch can eat several sell slots
        //    even though it only eats one carry slot.
        int remInventory = params.inventorySlots();
        int remListings = params.retainerListingSlots();
        int stackTarget = Math.max(1, params.retainerListingStackTarget());
        for (Candidate c : eligible) {
            if (picked.size() >= params.retainerSlots()) break;
            int listingsUsed = (c.qty() + stackTarget - 1) / stackTarget;
            if (c.totalCost() > remBudget) continue;
            if (c.slotsConsumed() > remInventory) continue;
            if (listingsUsed > remListings) continue;
            picked.add(c);
            pickedRetainerEv.put(c, retainerEv.get(c));
            remBudget -= c.totalCost();
            remInventory -= c.slotsConsumed();
            remListings -= listingsUsed;
        }
        var pickedSet = new HashSet<>(picked);
        var hop = new ArrayList<Candidate>(candidates.size() - picked.size());
        for (Candidate c : candidates) {
            if (!pickedSet.contains(c)) hop.add(c);
        }
        return new RetainerPartition(hop, picked, pickedRetainerEv);
    }

    private static PlannerParams withReducedResources(PlannerParams p, long budget, int inventory) {
        return new PlannerParams(
                p.homeWorldId(),
                p.homeDataCenterId(),
                budget,
                inventory,
                p.attentionBudgetHours(),
                p.attentionFraction(),
                p.hopWeightGilPerSecond(),
                p.maxWorlds(),
                p.candidateTopK(),
                p.tDcSeconds(),
                p.tRegionSeconds(),
                p.ilpMaxNodes(),
                p.retainerSlots(),
                p.retainerListingSlots(),
                p.retainerListingStackTarget(),
                p.retainerAttentionFraction(),
                p.retainerShelfHoursThreshold());
    }

    /**
     * Estimate an extra hop cost for retainer picks whose source worlds
     * aren't already covered by the hop plan. Rough model: assume each
     * new-to-the-route world costs one {@code tDcSeconds} hop (the
     * cheapest kind) — enough to warn callers who dangle a retainer pick
     * on a fresh DC that the trip isn't free.
     */
    private static int retainerExtraHopSeconds(
            List<RetainerPick> basket, Plan hopPlan, PlannerParams params, WorldNode home) {
        if (basket.isEmpty()) return 0;
        var visited = new HashSet<Integer>();
        visited.add(home.worldId());
        for (var stop : hopPlan.stops()) visited.add(stop.worldId());
        int extra = 0;
        for (var pick : basket) {
            if (visited.add(pick.sourceWorldId())) extra += params.tDcSeconds();
        }
        return extra;
    }

    /** Fold the retainer partition into the hop plan produced by {@link #assemble}. */
    private static Plan withRetainer(Plan hopPlan, RetainerPartition partition, PlannerParams params, WorldNode home) {
        if (partition.retainerPicks().isEmpty()) return hopPlan;
        var basket = new ArrayList<RetainerPick>(partition.retainerPicks().size());
        long spend = 0L;
        int qty = 0;
        int slots = 0;
        double retainerEvGross = 0.0;
        double retainerAttentionHours = 0.0;
        for (Candidate c : partition.retainerPicks()) {
            retainerAttentionHours += RETAINER_RUN_SHARE_SECONDS / 3600.0
                    + c.valuation().expectedTimeOnShelfHours() * params.retainerAttentionFraction();
            double rEv = partition.retainerEvPerHour().get(c);
            basket.add(new RetainerPick(
                    c.uniqueKey(),
                    c.itemId(),
                    c.itemName(),
                    c.hq(),
                    c.sourceWorldId(),
                    c.sourceWorldName(),
                    c.dataCenterId(),
                    c.dataCenterName(),
                    c.qty(),
                    c.slotsConsumed(),
                    c.buyPrice(),
                    c.valuation(),
                    rEv));
            spend += c.totalCost();
            qty += c.qty();
            slots += c.slotsConsumed();
            retainerEvGross += c.valuation().evGross();
        }
        int extraHopSeconds = retainerExtraHopSeconds(basket, hopPlan, params, home);
        double retainerObjective = retainerEvGross - (double) extraHopSeconds * params.hopWeightGilPerSecond();
        return new Plan(
                hopPlan.homeWorldId(),
                hopPlan.homeWorldName(),
                hopPlan.homeDataCenterId(),
                hopPlan.homeDataCenterName(),
                hopPlan.objective() + retainerObjective,
                hopPlan.totalEvGross() + Math.round(retainerEvGross),
                hopPlan.totalBuyCost() + spend,
                hopPlan.totalQty() + qty,
                hopPlan.totalSlots() + slots,
                hopPlan.totalAttentionHours(),
                hopPlan.totalHopSeconds() + extraHopSeconds,
                hopPlan.hopSecondsToHome(),
                hopPlan.candidatesConsidered(),
                hopPlan.subsetsConsidered(),
                hopPlan.stops(),
                basket,
                spend,
                qty,
                slots,
                retainerEvGross,
                hopPlan.crafts(),
                hopPlan.craftMaterialsCost(),
                hopPlan.craftEvGross(),
                hopPlan.desynths(),
                hopPlan.desynthBuyCost(),
                hopPlan.desynthEvGross(),
                retainerAttentionHours,
                retainerObjective);
    }

    private record RetainerPartition(
            List<Candidate> hopCandidates, List<Candidate> retainerPicks, Map<Candidate, Double> retainerEvPerHour) {
        long spend() {
            long total = 0;
            for (Candidate c : retainerPicks) total += c.totalCost();
            return total;
        }

        /** Sum of raw units bought — used for display totals ("bought 140 units"). */
        int units() {
            int total = 0;
            for (Candidate c : retainerPicks) total += c.qty();
            return total;
        }

        /** Sum of inventory slots consumed — this is what the hop planner budgets against. */
        int slots() {
            int total = 0;
            for (Candidate c : retainerPicks) total += c.slotsConsumed();
            return total;
        }

        static RetainerPartition emptyFor(List<Candidate> candidates) {
            return new RetainerPartition(candidates, List.of(), Map.of());
        }
    }

    // --- Assembly ----------------------------------------------------------

    private interface SubsetCallback {
        void accept(List<Integer> ids);
    }

    private static final class Best {
        // "Do nothing" is always available — a subset is only recorded if it beats an empty run.
        double objective = 0.0;
        double value = 0.0;
        List<Integer> subset = null;
        List<Candidate> picks = null;
        HopOrderer.Ordering ordering = null;
    }
}
