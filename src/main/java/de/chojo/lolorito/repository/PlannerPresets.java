/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Singleton;
import de.chojo.lolorito.entity.PlannerPreset;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/** CRUD for {@code planner_preset}. Every method scopes on {@code discord_user_id}. */
@Singleton
public class PlannerPresets {

    public List<PlannerPreset> list(long discordUserId) {
        return query("""
                SELECT id::text AS id, discord_user_id, name, params::text AS params,
                       created_at, updated_at
                  FROM planner_preset
                 WHERE discord_user_id = :u
                 ORDER BY updated_at DESC
                """)
                .single(call().bind("u", discordUserId))
                .map(PlannerPresets::readRow)
                .all();
    }

    public Optional<PlannerPreset> find(UUID id, long discordUserId) {
        return query("""
                SELECT id::text AS id, discord_user_id, name, params::text AS params,
                       created_at, updated_at
                  FROM planner_preset
                 WHERE id = :id::uuid AND discord_user_id = :u
                """)
                .single(call().bind("id", id, UUID_STRING).bind("u", discordUserId))
                .map(PlannerPresets::readRow)
                .first();
    }

    public PlannerPreset upsertByName(long discordUserId, String name, String paramsJson) {
        query("""
                INSERT INTO planner_preset (discord_user_id, name, params)
                VALUES (:u, :n, :p::jsonb)
                ON CONFLICT (discord_user_id, name)
                    DO UPDATE SET params = excluded.params, updated_at = now()
                """)
                .single(call().bind("u", discordUserId).bind("n", name).bind("p", paramsJson))
                .insert();
        return query("""
                SELECT id::text AS id, discord_user_id, name, params::text AS params,
                       created_at, updated_at
                  FROM planner_preset
                 WHERE discord_user_id = :u AND name = :n
                """)
                .single(call().bind("u", discordUserId).bind("n", name))
                .map(PlannerPresets::readRow)
                .first()
                .orElseThrow(() -> new IllegalStateException("Failed to upsert preset " + name));
    }

    public PlannerPreset rename(UUID id, long discordUserId, String newName) {
        query("""
                UPDATE planner_preset
                   SET name = :n, updated_at = now()
                 WHERE id = :id::uuid AND discord_user_id = :u
                """)
                .single(call().bind("n", newName).bind("id", id, UUID_STRING).bind("u", discordUserId))
                .update();
        return find(id, discordUserId).orElseThrow(() -> new IllegalStateException("Preset not found: " + id));
    }

    public boolean delete(UUID id, long discordUserId) {
        return query("DELETE FROM planner_preset WHERE id = :id::uuid AND discord_user_id = :u")
                        .single(call().bind("id", id, UUID_STRING).bind("u", discordUserId))
                        .delete()
                        .rows()
                > 0;
    }

    private static PlannerPreset readRow(Row row) throws SQLException {
        return new PlannerPreset(
                UUID.fromString(row.getString("id")),
                row.getLong("discord_user_id"),
                row.getString("name"),
                row.getString("params"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }

    /** Local wrapper so we can pass an {@code Instant} without an import creep. */
    static Instant nowInstant() {
        return Instant.now();
    }
}
