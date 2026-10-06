/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

import de.chojo.lolorito.value.Valuation;

/**
 * One line in the retainer-overnight basket. Same shape as a
 * {@link PlanBuy} plus the pre-computed retainer EV/hr so the SPA doesn't
 * have to re-derive it — the two numbers on a candidate differ by the
 * attention fraction alone.
 */
public record RetainerPick(
        String uniqueKey,
        int itemId,
        String itemName,
        boolean hq,
        int sourceWorldId,
        String sourceWorldName,
        int dataCenterId,
        String dataCenterName,
        int qty,
        int slots,
        int buyPrice,
        Valuation valuation,
        double retainerEvPerHour) {

    /** Total gil this pick consumes on the trip. */
    public long totalCost() {
        return (long) qty * buyPrice;
    }
}
