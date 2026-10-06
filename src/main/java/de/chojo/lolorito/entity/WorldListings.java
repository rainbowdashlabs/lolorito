/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import java.util.List;

/**
 * Per-world grouping: the world's stats + the individual listings on it.
 */
public record WorldListings(ItemStat stats, List<ItemListing> listings) {}
