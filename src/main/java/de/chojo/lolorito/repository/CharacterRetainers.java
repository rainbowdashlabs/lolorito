/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Singleton;
import de.chojo.lolorito.entity.CharacterRetainer;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/** SQL for {@code character_retainer}. Every read is owner-scoped. */
@Singleton
public class CharacterRetainers {

    public List<CharacterRetainer> list(long discordUserId) {
        return query("""
                SELECT discord_user_id, retainer_name, world_id, created_at, updated_at
                  FROM character_retainer
                 WHERE discord_user_id = :u
                 ORDER BY retainer_name ASC
                """)
                .single(call().bind("u", discordUserId))
                .map(CharacterRetainers::readRow)
                .all();
    }

    public void put(long discordUserId, int worldId, String retainerName) {
        query("""
                INSERT INTO character_retainer (discord_user_id, world_id, retainer_name)
                VALUES (:u, :w, :n)
                ON CONFLICT (discord_user_id, world_id, retainer_name)
                    DO UPDATE SET updated_at = now()
                """)
                .single(call().bind("u", discordUserId).bind("w", worldId).bind("n", retainerName))
                .insert();
    }

    public boolean delete(long discordUserId, int worldId, String retainerName) {
        return query("""
                        DELETE FROM character_retainer
                         WHERE discord_user_id = :u AND world_id = :w AND retainer_name = :n
                        """)
                        .single(call().bind("u", discordUserId)
                                .bind("w", worldId)
                                .bind("n", retainerName))
                        .delete()
                        .rows()
                > 0;
    }

    private static CharacterRetainer readRow(de.chojo.sadu.mapper.wrapper.Row row) throws java.sql.SQLException {
        return new CharacterRetainer(
                row.getLong("discord_user_id"),
                row.getString("retainer_name"),
                row.getInt("world_id"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
