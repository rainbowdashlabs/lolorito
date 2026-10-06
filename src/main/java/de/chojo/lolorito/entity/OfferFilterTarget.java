/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

/**
 * Search scope for {@link OfferFilterRow} — mirror of the persisted string.
 * The {@link #columnName()} value is baked into SQL by
 * {@code Items.bestOffers}, so it stays a static allowlist.
 */
public enum OfferFilterTarget {
    REGION("region_name"),
    DATA_CENTER("data_center");

    private final String columnName;

    OfferFilterTarget(String columnName) {
        this.columnName = columnName;
    }

    public String columnName() {
        return columnName;
    }
}
