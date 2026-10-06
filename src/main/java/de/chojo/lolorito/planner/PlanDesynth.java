/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

import de.chojo.lolorito.value.Valuation;

import java.util.List;

/**
 * One desynth the plan wants performed at home. The buy itself stays a
 * regular stop line (action DESYNTH — it's a real listing on a real
 * world); this record carries the at-home side: what comes out of the
 * desynth and the spread between the buy cost and the expected component
 * proceeds.
 *
 * @param uniqueKey       matches the DESYNTH candidate's key
 * @param itemId          the source item to desynth
 * @param itemName        display label for the source
 * @param hq              true when the cheaper source listing was HQ
 * @param qty             units bought (and desynthed)
 * @param buyPrice        unit price at the source listing
 * @param buyCost         {@code qty × buyPrice}
 * @param sourceWorldId   world the buy happens on
 * @param sourceWorldName display label for that world
 * @param desynthClass    crafter class required to desynth, null when unknown
 * @param desynthLevel    required desynth level, null when unknown
 * @param desynthVerified false when the caller has no stored desynth skills
 * @param outputs         expected components per desynth
 * @param valuation       desynth valuation for {@code qty} sources;
 *                        {@code evGross} is the expected profit over
 *                        {@code buyCost}, {@code expectedNet} the component
 *                        proceeds (net of tax) per source unit
 */
public record PlanDesynth(
        String uniqueKey,
        int itemId,
        String itemName,
        boolean hq,
        int qty,
        int buyPrice,
        long buyCost,
        int sourceWorldId,
        String sourceWorldName,
        String desynthClass,
        Integer desynthLevel,
        boolean desynthVerified,
        List<PlanDesynthOutput> outputs,
        Valuation valuation) {}
