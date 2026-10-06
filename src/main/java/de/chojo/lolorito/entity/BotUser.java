/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

/**
 * The bot's view of a Discord user — just the id. Anything that needs the
 * user's filter, offers, or session goes through the corresponding service.
 */
public record BotUser(long userId) {}
