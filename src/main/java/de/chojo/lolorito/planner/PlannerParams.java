/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

/**
 * All knobs {@link PlannerEngine} needs from the caller. Ranges and defaults
 * live in {@code config.planner.*}; the request layer clamps and fills.
 *
 * @param homeWorldId                 world the player will sell on and start from
 * @param homeDataCenterId            DC that owns the home world (hop cost anchor)
 * @param budget                      gil-in-pocket cap
 * @param inventorySlots              units the player can carry in a single run
 * @param attentionBudgetHours        hours of shelf-time the player will tolerate
 * @param attentionFraction           see {@link de.chojo.lolorito.value.UserPrefs}
 * @param hopWeightGilPerSecond       gil / second — turns hops into EV
 * @param maxWorlds                   hard cap on source worlds visited in the plan
 * @param candidateTopK               coarse cap after scoring, before the ILP
 * @param tDcSeconds                  per-hop cost within a data center
 * @param tRegionSeconds              per-hop cost across data centers of a region
 * @param ilpMaxNodes                 safety cap on B&amp;B iterations
 * @param retainerSlots               distinct-pick cap on the retainer partition
 * @param retainerListingSlots        total sell listings available across all
 *                                    retainers — one pick can consume several
 *                                    listings if it's split for velocity
 * @param retainerListingStackTarget  target stack size for split retainer
 *                                    listings (each pick consumes
 *                                    {@code ceil(qty / target)} listings)
 * @param retainerAttentionFraction   attention fraction applied to retainer picks
 * @param retainerShelfHoursThreshold minimum expected shelf hours to qualify as a retainer pick
 */
public record PlannerParams(
        int homeWorldId,
        int homeDataCenterId,
        long budget,
        int inventorySlots,
        double attentionBudgetHours,
        double attentionFraction,
        int hopWeightGilPerSecond,
        int maxWorlds,
        int candidateTopK,
        int tDcSeconds,
        int tRegionSeconds,
        int ilpMaxNodes,
        int retainerSlots,
        int retainerListingSlots,
        int retainerListingStackTarget,
        double retainerAttentionFraction,
        double retainerShelfHoursThreshold) {}
