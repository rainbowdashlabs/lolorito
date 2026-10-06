/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import de.chojo.lolorito.repository.ItemDetail;

import java.util.List;

/**
 * Depth-aware buy pricing over the cheap side of an item's book. Fixes
 * the "min-price × qty" fiction: the cheapest listing may hold a
 * single unit while the recipe needs twenty — the real cost climbs the
 * price steps.
 */
public final class ListingBook {

    private ListingBook() {}

    /**
     * Units past the fetched book depth are priced at the deepest known
     * level times this factor. Flat last-price was systematically
     * optimistic — the unseen tail of a book only ever gets more
     * expensive — so the remainder carries a pessimistic surcharge
     * instead. Callers control the fetched depth; the escalation only
     * matters for needs that outrun it.
     */
    public static final double REMAINDER_ESCALATION = 1.25;

    /**
     * Effective per-unit cost of acquiring {@code needed} units by
     * clearing the book cheapest-first. A remainder past the fetched
     * levels is priced at the deepest known level escalated by
     * {@link #REMAINDER_ESCALATION}. Returns null when the book is empty
     * or the need is non-positive.
     */
    public static Integer unitCostFor(List<ItemDetail.PriceLevel> levels, int needed) {
        return unitCostFor(levels, needed, 0);
    }

    /**
     * Same walk, but the first {@code skipUnits} units of book depth are
     * treated as already bought — the shared-consumption re-pricing the
     * plan assembly runs when several picks shop the same book.
     */
    public static Integer unitCostFor(List<ItemDetail.PriceLevel> levels, int needed, int skipUnits) {
        if (levels == null || levels.isEmpty() || needed <= 0) return null;
        long total = 0;
        int remaining = needed;
        int skip = Math.max(0, skipUnits);
        int lastPrice = 0;
        for (var level : levels) {
            if (remaining <= 0) break;
            int available = Math.max(0, level.quantity());
            int skipped = Math.min(skip, available);
            skip -= skipped;
            available -= skipped;
            int take = Math.min(remaining, available);
            total += (long) take * level.unitPrice();
            remaining -= take;
            lastPrice = level.unitPrice();
        }
        if (remaining > 0) {
            total += remaining * (long) Math.ceil(lastPrice * REMAINDER_ESCALATION);
        }
        return (int) Math.ceil(total / (double) needed);
    }
}
