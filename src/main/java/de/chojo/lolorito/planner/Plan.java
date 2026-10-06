/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

import java.util.List;

/**
 * The engine's output. Stops are ordered — the first one is the first world
 * the player visits from home, and {@code hopSecondsToHome} is the return
 * trip from the last stop back to the home world.
 *
 * <p>{@link #retainerBasket} holds picks that the retainer-overnight pool
 * peeled off before the hop solver ran — items with expected shelf-time
 * above the threshold that make more sense parked overnight than sold
 * actively. Retainer picks share the same {@code totalBuyCost} /
 * {@code totalQty} totals because the player pays for them and carries
 * them home on the same trip.
 *
 * <p>{@link #crafts} holds the CRAFT picks the solver chose, re-shaped
 * from "buy the product at ingredient cost" into a bill of materials plus
 * a craft list (see {@link PlanCraft}). The materials are folded into
 * {@link #stops} as MATERIAL buy lines — the player shops for them on the
 * same trip as the resale picks — while the craft section carries the
 * at-home side (pre-crafts, products, pay-vs-get spread).
 *
 * <p>{@link #desynths} holds the DESYNTH picks. Their buys are ordinary
 * stop lines (real listings on real worlds); the section carries the
 * expected components and the spread.
 *
 * @param objective            EV net of hop cost — the value the solver
 *                             maximised (adjusted for hops added by
 *                             material-only stops)
 * @param totalEvGross         raw sum of candidate EV, ignoring hops
 * @param totalBuyCost         gil spent across all stops
 * @param totalQty             units bought across all stops
 * @param totalAttentionHours  attention hours consumed by the live route;
 *                             retainer picks are counted separately in
 *                             {@code retainerAttentionHours}
 * @param totalHopSeconds      wall-clock hop budget for the round trip
 * @param candidatesConsidered how many prescored candidates entered the ILP
 * @param subsetsConsidered    how many world subsets the solver evaluated
 * @param craftMaterialsCost   gil for every bill of materials in {@link #crafts}
 * @param craftEvGross         expected profit (over materials) across {@link #crafts}
 * @param desynthBuyCost       gil spent on desynth sources
 * @param desynthEvGross       expected profit (over source cost) across {@link #desynths}
 * @param retainerAttentionHours attention hours the retainer basket costs at
 *                             the retainer attention fraction
 * @param retainerObjective    the retainer basket's share of {@code objective}:
 *                             its EV net of the extra hops it adds
 */
public record Plan(
        int homeWorldId,
        String homeWorldName,
        int homeDataCenterId,
        String homeDataCenterName,
        double objective,
        long totalEvGross,
        long totalBuyCost,
        int totalQty,
        int totalSlots,
        double totalAttentionHours,
        int totalHopSeconds,
        int hopSecondsToHome,
        int candidatesConsidered,
        int subsetsConsidered,
        List<PlanStop> stops,
        List<RetainerPick> retainerBasket,
        long retainerSpend,
        int retainerQty,
        int retainerSlots,
        double retainerEvGross,
        List<PlanCraft> crafts,
        long craftMaterialsCost,
        double craftEvGross,
        List<PlanDesynth> desynths,
        long desynthBuyCost,
        double desynthEvGross,
        double retainerAttentionHours,
        double retainerObjective) {

    public static Plan empty(int homeWorldId, String homeWorldName, int homeDataCenterId, String homeDataCenterName) {
        return new Plan(
                homeWorldId,
                homeWorldName,
                homeDataCenterId,
                homeDataCenterName,
                0.0,
                0L,
                0L,
                0,
                0,
                0.0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                0L,
                0,
                0,
                0.0,
                List.of(),
                0L,
                0.0,
                List.of(),
                0L,
                0.0,
                0.0,
                0.0);
    }

    /**
     * Copy with the CRAFT picks re-shaped: the caller passes the rebuilt
     * stop list (craft pseudo-buys removed, material lines folded in,
     * possibly with extra material-only stops) and the craft section.
     * Totals are corrected by the deltas the rebuild produced: material
     * lines replace the craft candidates' product-shaped cost/qty/slots.
     * The objective stays untouched — the solver already charged the
     * material-world hops when it picked the candidates.
     *
     * @param deltaBuyCost    materials cost minus extracted craft-buy cost
     * @param deltaQty        material units minus extracted product units
     * @param deltaSlots      material slots minus extracted product slots
     * @param deltaHopSeconds hop seconds added by material-only stops
     * @param hopSecondsToHome return hop from the (possibly new) last stop
     */
    public Plan withCraftSection(
            List<PlanStop> stops,
            List<PlanCraft> crafts,
            long deltaBuyCost,
            int deltaQty,
            int deltaSlots,
            int deltaHopSeconds,
            int hopSecondsToHome) {
        long materialsCost = 0;
        double evGross = 0.0;
        for (PlanCraft craft : crafts) {
            materialsCost += craft.materialsCost();
            evGross += craft.valuation().evGross();
        }
        return new Plan(
                homeWorldId,
                homeWorldName,
                homeDataCenterId,
                homeDataCenterName,
                objective,
                totalEvGross,
                totalBuyCost + deltaBuyCost,
                totalQty + deltaQty,
                totalSlots + deltaSlots,
                totalAttentionHours,
                totalHopSeconds + deltaHopSeconds,
                hopSecondsToHome,
                candidatesConsidered,
                subsetsConsidered,
                stops,
                retainerBasket,
                retainerSpend,
                retainerQty,
                retainerSlots,
                retainerEvGross,
                crafts,
                materialsCost,
                evGross,
                desynths,
                desynthBuyCost,
                desynthEvGross,
                retainerAttentionHours,
                retainerObjective);
    }

    /**
     * Copy with the DESYNTH section attached. The stop lines may have
     * been re-priced by the shared-book pass (a craft or another desynth
     * consumed the cheapest listings first), so the caller passes the
     * rebuilt stops and the total price drift.
     */
    public Plan withDesynthSection(List<PlanStop> stops, List<PlanDesynth> desynths, long deltaBuyCost) {
        long buyCost = 0;
        double evGross = 0.0;
        for (PlanDesynth d : desynths) {
            buyCost += d.buyCost();
            evGross += d.valuation().evGross();
        }
        return new Plan(
                homeWorldId,
                homeWorldName,
                homeDataCenterId,
                homeDataCenterName,
                objective,
                totalEvGross,
                totalBuyCost + deltaBuyCost,
                totalQty,
                totalSlots,
                totalAttentionHours,
                totalHopSeconds,
                hopSecondsToHome,
                candidatesConsidered,
                subsetsConsidered,
                stops,
                retainerBasket,
                retainerSpend,
                retainerQty,
                retainerSlots,
                retainerEvGross,
                crafts,
                craftMaterialsCost,
                craftEvGross,
                desynths,
                buyCost,
                evGross,
                retainerAttentionHours,
                retainerObjective);
    }
}
