/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

import de.chojo.lolorito.value.Valuation;

/**
 * One line item on a stop — buy {@code qty} of {@code itemId} for
 * {@code buyPrice} apiece and follow {@link #action} home.
 *
 * @param valuation     the line's own sell-side score; null for MATERIAL
 *                      lines, whose value flows through the craft they feed
 * @param craftClass    required class when {@link #action} is CRAFT or
 *                      DESYNTH, null otherwise
 * @param craftLevel    required class level for CRAFT/DESYNTH lines, null
 *                      otherwise
 * @param craftVerified false when the requirement came from the
 *                      no-stored-skills fallback — shown but unchecked
 */
public record PlanBuy(
        String uniqueKey,
        int itemId,
        String itemName,
        boolean hq,
        int qty,
        int slots,
        int buyPrice,
        PlanAction action,
        Valuation valuation,
        String craftClass,
        Integer craftLevel,
        boolean craftVerified) {

    public long totalCost() {
        return (long) qty * buyPrice;
    }
}
