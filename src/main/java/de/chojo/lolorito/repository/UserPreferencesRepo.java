/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Singleton;
import de.chojo.lolorito.entity.UserPreferences;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/** SQL for the {@code user_preferences} table (locale/theme persistence). */
@Singleton
public class UserPreferencesRepo {

    public Optional<UserPreferences> find(long discordUserId) {
        return query("""
                SELECT discord_user_id, locale, theme, planner_params::text AS planner_params,
                       alert_webhook_url
                  FROM user_preferences WHERE discord_user_id = :u
                """)
                .single(call().bind("u", discordUserId))
                .map(row -> new UserPreferences(
                        row.getLong("discord_user_id"),
                        row.getString("locale"),
                        row.getString("theme"),
                        row.getString("planner_params"),
                        row.getString("alert_webhook_url")))
                .first();
    }

    /**
     * Upsert the webhook URL. Pass an empty/null string to clear it —
     * the persisted value is normalised to {@code NULL} so downstream
     * dispatchers see it as "no webhook configured".
     */
    public UserPreferences upsertAlertWebhookUrl(long discordUserId, String url) {
        String normalised = (url == null || url.isBlank()) ? null : url.trim();
        query("""
                INSERT INTO user_preferences (discord_user_id, alert_webhook_url)
                VALUES (:u, :w)
                ON CONFLICT (discord_user_id)
                    DO UPDATE SET alert_webhook_url = excluded.alert_webhook_url, updated_at = now()
                """).single(call().bind("u", discordUserId).bind("w", normalised)).insert();
        return find(discordUserId).orElseGet(() -> UserPreferences.defaultFor(discordUserId));
    }

    public UserPreferences upsertPlannerParams(long discordUserId, String plannerParamsJson) {
        // The column is JSONB; sadu binds the string and we cast on write.
        query("""
                INSERT INTO user_preferences (discord_user_id, planner_params)
                VALUES (:u, :p::jsonb)
                ON CONFLICT (discord_user_id)
                    DO UPDATE SET planner_params = excluded.planner_params, updated_at = now()
                """)
                .single(call().bind("u", discordUserId).bind("p", plannerParamsJson))
                .insert();
        return find(discordUserId).orElseGet(() -> UserPreferences.defaultFor(discordUserId));
    }

    public UserPreferences upsertLocale(long discordUserId, String locale) {
        query("""
                INSERT INTO user_preferences (discord_user_id, locale)
                VALUES (:u, :l)
                ON CONFLICT (discord_user_id)
                    DO UPDATE SET locale = excluded.locale, updated_at = now()
                """).single(call().bind("u", discordUserId).bind("l", locale)).insert();
        return find(discordUserId).orElseGet(() -> UserPreferences.defaultFor(discordUserId));
    }

    public UserPreferences upsertTheme(long discordUserId, String theme) {
        query("""
                INSERT INTO user_preferences (discord_user_id, theme)
                VALUES (:u, :t)
                ON CONFLICT (discord_user_id)
                    DO UPDATE SET theme = excluded.theme, updated_at = now()
                """).single(call().bind("u", discordUserId).bind("t", theme)).insert();
        return find(discordUserId).orElseGet(() -> UserPreferences.defaultFor(discordUserId));
    }
}
