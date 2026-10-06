/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import java.time.Instant;

/**
 * One user's cached Lodestone character. {@link #profileJson} is a raw
 * JSON blob written by {@link de.chojo.lolorito.service.LodestoneClient}
 * — schema-less on purpose so we can add extracted fields without a
 * migration.
 */
public record CharacterProfile(
        long discordUserId, long lodestoneId, String profileJson, Instant fetchedAt, Instant updatedAt) {}
