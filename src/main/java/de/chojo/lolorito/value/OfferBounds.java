/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

/**
 * Caller-side limits on how much of one offer can actually be bought: the
 * gil in pocket and the free inventory slots. {@code 0} means unbounded.
 */
public record OfferBounds(int budget, int inventorySlots) {

    /** No limits. */
    public static final OfferBounds NONE = new OfferBounds(0, 0);

    /**
     * Largest quantity of {@code available} units at {@code unitPrice} that
     * fits both bounds; {@code 0} when not even one unit fits.
     *
     * @param stackSize units per inventory slot, at least 1
     */
    public int clamp(int available, int unitPrice, int stackSize) {
        long qty = Math.max(0, available);
        if (budget > 0 && unitPrice > 0) qty = Math.min(qty, budget / unitPrice);
        if (inventorySlots > 0) qty = Math.min(qty, (long) inventorySlots * Math.max(1, stackSize));
        return (int) qty;
    }
}
