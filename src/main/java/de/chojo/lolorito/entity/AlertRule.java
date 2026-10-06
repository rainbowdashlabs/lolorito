/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * A user-owned market-board alert. Watches a single item on either a world
 * or a data center; fires when the cheapest current listing crosses
 * {@link #thresholdPrice()} in the direction of {@link #kind()}. HQ can be
 * pinned to {@code true}, {@code false}, or left null for "either".
 *
 * <p>The scanner throttles re-fires by {@link #cooldownMinutes()} —
 * {@code lastTriggeredAt} + cooldown must be in the past for the rule to
 * fire again.
 */
public record AlertRule(
        UUID id,
        long userId,
        int itemId,
        AlertScope scope,
        Boolean hq,
        AlertKind kind,
        int thresholdPrice,
        boolean enabled,
        int cooldownMinutes,
        Instant lastTriggeredAt,
        Instant createdAt) {}
