/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import java.time.Instant;

/**
 * One (user, kind, class) skill level. {@code kind} is one of
 * {@code craft} or {@code desynth}; {@code className} is the canonical
 * FFXIV job name (carpenter, blacksmith, armorer, goldsmith,
 * leatherworker, weaver, alchemist, culinarian). Level 0 means
 * "not tracked" — the planner treats it as unavailable.
 */
public record UserSkillLevel(long discordUserId, SkillKind kind, String className, int level, Instant updatedAt) {}
