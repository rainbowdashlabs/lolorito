/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import de.chojo.universalis.entities.Item;
import de.chojo.universalis.entities.Price;
import de.chojo.universalis.worlds.World;

import java.time.Instant;

/**
 * One current listing row for {@code (world, item, hq)} at {@code price}.
 */
public record ItemListing(World world, Item item, boolean hq, Price price, Instant updated) {}
