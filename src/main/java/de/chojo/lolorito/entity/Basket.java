/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A saved basket the user built in the SPA — owner, share token, items.
 */
public record Basket(
        UUID id,
        long ownerUserId,
        String shareToken,
        String name,
        BasketVisibility visibility,
        Instant createdAt,
        Instant updatedAt,
        List<BasketItem> items) {}
