/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.entity.AlertKind;
import de.chojo.lolorito.entity.AlertRule;

import java.time.Duration;
import java.time.Instant;

/** Pure decision logic: given a rule and a probed cheapest-current price, should we fire? */
public final class AlertMatcher {

    private AlertMatcher() {}

    /**
     * @param rule the rule under evaluation
     * @param cheapestPrice the current cheapest listing on the rule's scope,
     *                      or {@code null} if no listings are available
     * @param now clock value
     * @return true iff the rule is enabled, its threshold is crossed in the
     *         configured direction, and the cooldown has elapsed
     */
    public static boolean shouldFire(AlertRule rule, Integer cheapestPrice, Instant now) {
        if (!rule.enabled()) return false;
        if (cheapestPrice == null || cheapestPrice <= 0) return false;
        if (!thresholdCrossed(rule.kind(), cheapestPrice, rule.thresholdPrice())) return false;
        return cooldownElapsed(rule.lastTriggeredAt(), rule.cooldownMinutes(), now);
    }

    static boolean thresholdCrossed(AlertKind kind, int cheapest, int threshold) {
        return switch (kind) {
            case PRICE_BELOW -> cheapest <= threshold;
            case PRICE_ABOVE -> cheapest >= threshold;
        };
    }

    static boolean cooldownElapsed(Instant last, int cooldownMinutes, Instant now) {
        if (last == null) return true;
        return !now.isBefore(last.plus(Duration.ofMinutes(Math.max(0, cooldownMinutes))));
    }
}
