/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * One saved planner-form preset. The {@code paramsJson} field holds the
 * exact JSON payload the SPA POSTed — the backend doesn't parse it beyond
 * length checks so new form fields don't need a schema migration.
 */
public record PlannerPreset(
        UUID id, long discordUserId, String name, String paramsJson, Instant createdAt, Instant updatedAt) {}
