/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Singleton;
import de.chojo.lolorito.entity.SkillKind;
import de.chojo.lolorito.entity.UserSkillLevel;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/** SQL for {@code user_skill_level}. Every read is owner-scoped. */
@Singleton
public class UserSkillLevels {

    public List<UserSkillLevel> list(long discordUserId) {
        return query("""
                SELECT discord_user_id, kind, class_name, level, updated_at
                  FROM user_skill_level
                 WHERE discord_user_id = :u
                 ORDER BY kind ASC, class_name ASC
                """)
                .single(call().bind("u", discordUserId))
                .map(UserSkillLevels::readRow)
                .all();
    }

    /** Upsert. Rows with {@code level=0} are treated as "not tracked" and get deleted. */
    public void put(long discordUserId, SkillKind kind, String className, int level) {
        if (level <= 0) {
            query("""
                    DELETE FROM user_skill_level
                     WHERE discord_user_id = :u AND kind = :k AND class_name = :c
                    """)
                    .single(call().bind("u", discordUserId)
                            .bind("k", kind.wire())
                            .bind("c", className))
                    .delete();
            return;
        }
        query("""
                INSERT INTO user_skill_level (discord_user_id, kind, class_name, level)
                VALUES (:u, :k, :c, :l)
                ON CONFLICT (discord_user_id, kind, class_name)
                    DO UPDATE SET level = EXCLUDED.level, updated_at = now()
                """)
                .single(call().bind("u", discordUserId)
                        .bind("k", kind.wire())
                        .bind("c", className)
                        .bind("l", level))
                .insert();
    }

    private static UserSkillLevel readRow(de.chojo.sadu.mapper.wrapper.Row row) throws java.sql.SQLException {
        return new UserSkillLevel(
                row.getLong("discord_user_id"),
                SkillKind.fromWire(row.getString("kind")),
                row.getString("class_name"),
                row.getInt("level"),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
