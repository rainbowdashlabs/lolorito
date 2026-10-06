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

/** Pure decision logic: given a rule and the value observed for its kind, should we fire? */
public final class AlertMatcher {

    /** Daily-volume floor for the spike baseline, so a usually silent item doesn't spike on one sale. */
    static final double MIN_BASELINE_UNITS_PER_DAY = 1.0;

    private AlertMatcher() {}

    /**
     * @param rule     the rule under evaluation
     * @param observed the value for the rule's kind (cheapest price, spike
     *                 percent, or listing count), or {@code null} when there
     *                 is no fresh data to judge by
     * @param now      clock value
     * @return true iff the rule is enabled, its threshold is crossed in the
     *         kind's direction, and the cooldown has elapsed
     */
    public static boolean shouldFire(AlertRule rule, Integer observed, Instant now) {
        if (!rule.enabled()) return false;
        if (observed == null || observed < 0) return false;
        if (rule.kind().isPrice() && observed == 0) return false;
        if (!thresholdCrossed(rule.kind(), observed, rule.threshold())) return false;
        return cooldownElapsed(rule.lastTriggeredAt(), rule.cooldownMinutes(), now);
    }

    static boolean thresholdCrossed(AlertKind kind, int observed, int threshold) {
        return switch (kind) {
            case PRICE_BELOW, LISTING_COUNT_DROP -> observed <= threshold;
            case PRICE_ABOVE, SALE_VOLUME_SPIKE -> observed >= threshold;
        };
    }

    /**
     * Units sold in the last 24 hours as a percent of the trailing daily
     * average.
     *
     * @param recentUnits   units sold in the last 24 hours
     * @param baselineUnits units sold in the {@code baselineDays} before that
     */
    public static int spikePercent(long recentUnits, long baselineUnits, int baselineDays) {
        double perDay = Math.max(MIN_BASELINE_UNITS_PER_DAY, baselineUnits / (double) Math.max(1, baselineDays));
        return (int) Math.min(Integer.MAX_VALUE, Math.round(100.0 * recentUnits / perDay));
    }

    /** An observed value that crosses {@code rule}'s threshold, for test dispatches. */
    public static int syntheticObservation(AlertRule rule) {
        return switch (rule.kind()) {
            case PRICE_BELOW -> Math.max(1, rule.threshold() - 1);
            case PRICE_ABOVE -> rule.threshold() + 1;
            case SALE_VOLUME_SPIKE -> rule.threshold() + 50;
            case LISTING_COUNT_DROP -> Math.max(0, rule.threshold() - 1);
        };
    }

    static boolean cooldownElapsed(Instant last, int cooldownMinutes, Instant now) {
        if (last == null) return true;
        return !now.isBefore(last.plus(Duration.ofMinutes(Math.max(0, cooldownMinutes))));
    }
}
