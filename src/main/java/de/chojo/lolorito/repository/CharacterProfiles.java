/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Singleton;
import de.chojo.lolorito.entity.CharacterProfile;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/** SQL for {@code character_profile}. Every read is owner-scoped. */
@Singleton
public class CharacterProfiles {

    public Optional<CharacterProfile> find(long discordUserId) {
        return query("""
                SELECT discord_user_id, lodestone_id, profile::text AS profile, fetched_at, updated_at
                  FROM character_profile
                 WHERE discord_user_id = :u
                """)
                .single(call().bind("u", discordUserId))
                .map(row -> new CharacterProfile(
                        row.getLong("discord_user_id"),
                        row.getLong("lodestone_id"),
                        row.getString("profile"),
                        row.get("fetched_at", INSTANT_TIMESTAMP),
                        row.get("updated_at", INSTANT_TIMESTAMP)))
                .first();
    }

    public CharacterProfile upsert(long discordUserId, long lodestoneId, String profileJson) {
        query("""
                INSERT INTO character_profile (discord_user_id, lodestone_id, profile)
                VALUES (:u, :l, :p::jsonb)
                ON CONFLICT (discord_user_id) DO UPDATE
                   SET lodestone_id = excluded.lodestone_id,
                       profile      = excluded.profile,
                       fetched_at   = now(),
                       updated_at   = now()
                """)
                .single(call().bind("u", discordUserId).bind("l", lodestoneId).bind("p", profileJson))
                .insert();
        return find(discordUserId)
                .orElseThrow(
                        () -> new IllegalStateException("Failed to upsert character profile for " + discordUserId));
    }

    public boolean delete(long discordUserId) {
        return query("DELETE FROM character_profile WHERE discord_user_id = :u")
                        .single(call().bind("u", discordUserId))
                        .delete()
                        .rows()
                > 0;
    }
}
