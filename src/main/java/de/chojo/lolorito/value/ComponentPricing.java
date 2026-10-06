/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

/**
 * Bundle of a component's home-world market model plus the average quantity
 * the caller expects to obtain per source unit. Used by both
 * {@link ValueEngine#valueDesynth} (average desynth yield) and
 * {@link ValueEngine#valueCraft} (recipe ingredient count).
 *
 * @param itemId          component/ingredient item id
 * @param avgQtyPerSource units yielded (desynth) or consumed (craft) per
 *                        unit of the source/product
 * @param sellModel       fitted market model on the home world; {@code null}
 *                        when unavailable
 * @param cheapestBuy     cheapest known cross-world listing price; used by
 *                        {@link ValueEngine#valueCraft} to price ingredients.
 *                        Ignored by desynth valuation.
 */
public record ComponentPricing(int itemId, double avgQtyPerSource, MarketModel sellModel, Integer cheapestBuy) {}
