/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

/**
 * What an alert rule watches, and what its {@code threshold} means for it.
 */
public enum AlertKind {
    /** Fires when the cheapest current listing sits at or below {@code threshold} gil. */
    PRICE_BELOW,
    /** Fires when the cheapest current listing sits at or above {@code threshold} gil. */
    PRICE_ABOVE,
    /**
     * Fires when units sold in the last 24 hours reach {@code threshold} percent
     * of the trailing daily average (200 = twice the usual volume).
     */
    SALE_VOLUME_SPIKE,
    /** Fires when the number of current listings drops to {@code threshold} or fewer. */
    LISTING_COUNT_DROP;

    public String wire() {
        return name().toLowerCase();
    }

    /** True for the kinds whose observed value is a listing price. */
    public boolean isPrice() {
        return this == PRICE_BELOW || this == PRICE_ABOVE;
    }

    /** Smallest threshold that makes sense: a listing count may watch for zero, everything else needs at least 1. */
    public int minThreshold() {
        return this == LISTING_COUNT_DROP ? 0 : 1;
    }

    public static AlertKind fromWire(String s) {
        if (s == null) return PRICE_BELOW;
        return switch (s.toLowerCase()) {
            case "price_above" -> PRICE_ABOVE;
            case "sale_volume_spike" -> SALE_VOLUME_SPIKE;
            case "listing_count_drop" -> LISTING_COUNT_DROP;
            default -> PRICE_BELOW;
        };
    }
}
