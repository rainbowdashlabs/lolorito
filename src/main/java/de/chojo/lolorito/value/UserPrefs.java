/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

/**
 * Everything the value engine needs from the user that isn't a market fact.
 *
 * @param mbTax             market-board sale tax, 0..1 (e.g. 0.05 for 5 %)
 * @param attentionFraction how much shelf-time actually costs you —
 *                          {@code 1.0} = watching the market board, going
 *                          toward 0 = happy for retainers to sit
 * @param tRunShareSeconds  wall-clock allocated to the shopping run itself
 *                          per candidate (travel + purchase + listing).
 *                          Used to spread the fixed cost across the basket.
 */
public record UserPrefs(double mbTax, double attentionFraction, double tRunShareSeconds) {
    /**
     * Sensible defaults for a solo player active on the board.
     */
    public static UserPrefs defaults() {
        return new UserPrefs(0.05, 0.25, 30.0);
    }
}
