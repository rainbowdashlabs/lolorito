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
 * One "buy elsewhere, sell home" arbitrage row — same shape as
 * {@link ItemListing} plus the home-vs-source factor and raw profit at the
 * candidate quantity.
 */
public record OfferListing(
        World world, Item item, boolean hq, Price price, Instant updated, double factor, int profit) {}
