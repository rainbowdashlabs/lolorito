/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import java.util.List;

/**
 * Per-world grouping: the world's stats + the arbitrage rows on it.
 */
public record WorldOffers(ItemStat itemStat, List<OfferListing> listings) {}
