/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import java.time.Instant;

/**
 * One retainer a user has declared. Attribution is retainer-name-only —
 * the game stopped exposing the owner id, so there is no way to link
 * retainers to a shared account automatically. Users have to declare
 * every retainer they own, and each retainer needs at least one active
 * listing before Universalis observes it.
 */
public record CharacterRetainer(
        long discordUserId, String retainerName, int worldId, Instant createdAt, Instant updatedAt) {}
