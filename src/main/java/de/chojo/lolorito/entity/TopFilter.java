/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

public record TopFilter(
        SearchScope searchScope,
        SortOrder order,
        Boolean hq,
        Integer minSales,
        Double minPopularity,
        Double minInterest,
        Double minMarketVolume,
        Double minPrice,
        Double minAvgPrice) {}
