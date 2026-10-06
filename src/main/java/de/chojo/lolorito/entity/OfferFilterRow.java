/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

/**
 * Persisted shape of {@code offer_filter}. Both the web filter service and
 * the Discord {@code /offers filter} command read/write this record; the
 * legacy embedded-mutator entity has been replaced by
 * {@code FilterService.patch}.
 */
public record OfferFilterRow(
        int worldId,
        int offerLimit,
        int unitPrice,
        double factor,
        int refreshHours,
        double popularity,
        double marketVolume,
        double interest,
        int sales,
        int views,
        int profit,
        int effectiveProfit,
        String target) {

    /**
     * Sane defaults when a user has never edited a filter.
     */
    public static OfferFilterRow defaults() {
        return new OfferFilterRow(-1, 1000, 1000, 2.0, 1, 0.0, 0.0, 0.0, 0, 0, 100, 10000, "DATA_CENTER");
    }

    public OfferFilterTarget targetEnum() {
        try {
            return OfferFilterTarget.valueOf(target);
        } catch (IllegalArgumentException e) {
            return OfferFilterTarget.DATA_CENTER;
        }
    }
}
