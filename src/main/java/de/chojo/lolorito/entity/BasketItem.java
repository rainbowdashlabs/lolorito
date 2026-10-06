/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import java.time.Instant;

/**
 * One line in a saved {@link Basket}. Mirrors the SPA-side {@code BasketItem}
 * shape 1:1 so the JSON on the wire matches what the frontend already uses.
 */
public record BasketItem(
        String key,
        int itemId,
        String itemName,
        boolean hq,
        int sourceWorldId,
        String sourceWorldName,
        int quantity,
        int buyPrice,
        String action,
        double evPerHour,
        Instant addedAt,
        int position) {}
