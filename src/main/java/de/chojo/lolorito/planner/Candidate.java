/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

import de.chojo.lolorito.value.Valuation;

import java.util.List;

/**
 * One prescored buy the {@link PlannerEngine} can pick or skip. The
 * {@link #valuation} already reflects {@link #qty}, {@link #buyPrice} and
 * the user's attention prefs — the ILP just packs candidates into the
 * budget/inventory/attention capacities and adds a hop cost per world.
 *
 * @param uniqueKey       stable id used to dedupe basket / step-through
 * @param itemId          the item being bought
 * @param itemName        display label (English via NameSupplier)
 * @param hq              quality flag
 * @param sourceWorldId   world we buy from
 * @param sourceWorldName display label for the source world
 * @param dataCenterId    DC of the source world (used for hop costing)
 * @param dataCenterName  display label for the source DC
 * @param regionName      region of the source world (cross-region hops
 *                        aren't reachable, see {@link HopCoster})
 * @param qty             units bought — one candidate = one bucket
 * @param buyPrice        unit price at the source listing
 * @param action          which lane of the item DAG this candidate is
 * @param valuation       score for {@code (qty × buyPrice)}
 * @param craftClass      crafter class a CRAFT candidate requires; null
 *                        for resale/desynth lanes
 * @param craftLevel      required class level for a CRAFT candidate;
 *                        null for other lanes
 * @param craftVerified   false when the CRAFT candidate came from the
 *                        no-stored-skills fallback — the requirement is
 *                        shown but unchecked; true otherwise
 */
public record Candidate(
        String uniqueKey,
        int itemId,
        String itemName,
        boolean hq,
        int sourceWorldId,
        String sourceWorldName,
        int dataCenterId,
        String dataCenterName,
        String regionName,
        int qty,
        int buyPrice,
        int stackSize,
        PlanAction action,
        Valuation valuation,
        String craftClass,
        Integer craftLevel,
        boolean craftVerified,
        /**
         * Worlds the candidate's bill of materials shops on beyond the
         * source world — the solver charges their hops when this
         * candidate is picked. Empty for non-CRAFT lanes.
         */
        List<WorldNode> materialWorlds) {

    public Candidate {
        materialWorlds = materialWorlds == null ? List.of() : List.copyOf(materialWorlds);
    }

    /** Resale/desynth lane — no craft requirement attached. */
    public static Candidate simple(
            String uniqueKey,
            int itemId,
            String itemName,
            boolean hq,
            int sourceWorldId,
            String sourceWorldName,
            int dataCenterId,
            String dataCenterName,
            String regionName,
            int qty,
            int buyPrice,
            int stackSize,
            PlanAction action,
            Valuation valuation) {
        return new Candidate(
                uniqueKey,
                itemId,
                itemName,
                hq,
                sourceWorldId,
                sourceWorldName,
                dataCenterId,
                dataCenterName,
                regionName,
                qty,
                buyPrice,
                stackSize,
                action,
                valuation,
                null,
                null,
                true,
                List.of());
    }

    /** Fallback stack size when a caller hasn't looked up the real value. */
    public static final int DEFAULT_STACK_SIZE = 999;

    /**
     * Total gil to buy this candidate in one go.
     */
    public long totalCost() {
        return (long) qty * buyPrice;
    }

    /**
     * Inventory slots this candidate consumes. FFXIV stacks up to
     * {@link #stackSize} identical units in a single slot; buying a
     * partial stack still eats one slot. Non-stackable gear has
     * {@code stackSize = 1} so slots == qty for that case.
     */
    public int slotsConsumed() {
        int stack = Math.max(1, stackSize);
        return (qty + stack - 1) / stack;
    }

    /**
     * Attention this candidate consumes if included — {@code qty ×
     * shelfHoursPerUnit × attentionFraction}. Prefs already fold the
     * fraction into {@link Valuation#evPerHour()} but the ILP needs the raw
     * attention hours to enforce the capacity constraint.
     */
    public double attentionHours(double attentionFraction) {
        double shelf = valuation.expectedTimeOnShelfHours();
        if (!Double.isFinite(shelf)) return Double.POSITIVE_INFINITY;
        return shelf * attentionFraction;
    }
}
