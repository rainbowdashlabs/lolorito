/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

/** Direction of a price-based alert rule. */
public enum AlertKind {
    /** Fires when the cheapest current listing sits at or below {@code thresholdPrice}. */
    PRICE_BELOW,
    /** Fires when the cheapest current listing sits at or above {@code thresholdPrice}. */
    PRICE_ABOVE;

    public String wire() {
        return name().toLowerCase();
    }

    public static AlertKind fromWire(String s) {
        if (s == null) return PRICE_BELOW;
        return switch (s.toLowerCase()) {
            case "price_above" -> PRICE_ABOVE;
            default -> PRICE_BELOW;
        };
    }
}
