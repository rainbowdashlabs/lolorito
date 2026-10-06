/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

import java.util.List;

/**
 * A single world the player must visit on the run. The plan orders these
 * top-to-bottom; the SPA renders one card per stop and lets the player tick
 * them off which fires the replan endpoint.
 *
 * @param hopSecondsFromPrev seconds paid to reach this stop from the previous
 *                           one (or from home for the first stop)
 * @param totalQty           units bought at this stop across all buys
 * @param totalSlots         inventory slots consumed at this stop —
 *                           usually smaller than {@code totalQty} because
 *                           stackable items fold multiple units into a
 *                           single slot
 * @param buyCost            gil spent at this stop
 */
public record PlanStop(
        int worldId,
        String worldName,
        int dataCenterId,
        String dataCenterName,
        int hopSecondsFromPrev,
        int totalQty,
        int totalSlots,
        long buyCost,
        List<PlanBuy> buys) {}
