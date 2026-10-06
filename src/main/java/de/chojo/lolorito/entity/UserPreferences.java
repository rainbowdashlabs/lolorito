/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

/**
 * Per-user preferences that aren't tied to any single feature — locale,
 * theme, and whatever else lands in the settings tray. Stored in
 * {@code user_preferences} keyed by Discord user id.
 */
public record UserPreferences(
        long discordUserId, String locale, String theme, String plannerParamsJson, String alertWebhookUrl) {

    public static UserPreferences defaultFor(long discordUserId) {
        return new UserPreferences(discordUserId, "en", null, null, null);
    }
}
