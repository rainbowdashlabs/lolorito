/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Singleton;
import de.chojo.lolorito.entity.RetainerSale;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/** SQL for {@code retainer_sale}. */
@Singleton
public class RetainerSales {

    public RetainerSale insert(
            long discordUserId,
            String retainerName,
            int itemId,
            int worldId,
            boolean hq,
            long unitPrice,
            int quantity,
            Instant soldAt,
            String note) {
        return query("""
                INSERT INTO retainer_sale
                    (discord_user_id, retainer_name, item_id, world_id, hq, unit_price, quantity, sold_at, note)
                VALUES (:u, :r, :i, :w, :hq, :p, :q, :t, :n)
                RETURNING id, discord_user_id, retainer_name, item_id, world_id, hq, unit_price,
                          quantity, sold_at, note
                """)
                .single(call().bind("u", discordUserId)
                        .bind("r", retainerName)
                        .bind("i", itemId)
                        .bind("w", worldId)
                        .bind("hq", hq)
                        .bind("p", unitPrice)
                        .bind("q", quantity)
                        .bind("t", soldAt, INSTANT_TIMESTAMP)
                        .bind("n", note))
                .map(RetainerSales::readRow)
                .first()
                .orElseThrow(() -> new IllegalStateException("Failed to insert retainer sale row"));
    }

    public List<RetainerSale> recent(long discordUserId, int limit) {
        return query("""
                SELECT id, discord_user_id, retainer_name, item_id, world_id, hq, unit_price,
                       quantity, sold_at, note
                  FROM retainer_sale
                 WHERE discord_user_id = :u
                 ORDER BY sold_at DESC
                 LIMIT :l
                """)
                .single(call().bind("u", discordUserId).bind("l", Math.max(1, limit)))
                .map(RetainerSales::readRow)
                .all();
    }

    public Optional<RetainerSale> byId(long id, long discordUserId) {
        return query("""
                SELECT id, discord_user_id, retainer_name, item_id, world_id, hq, unit_price,
                       quantity, sold_at, note
                  FROM retainer_sale WHERE id = :id AND discord_user_id = :u
                """)
                .single(call().bind("id", id).bind("u", discordUserId))
                .map(RetainerSales::readRow)
                .first();
    }

    public boolean delete(long id, long discordUserId) {
        return query("DELETE FROM retainer_sale WHERE id = :id AND discord_user_id = :u")
                        .single(call().bind("id", id).bind("u", discordUserId))
                        .delete()
                        .rows()
                > 0;
    }

    private static RetainerSale readRow(Row row) throws SQLException {
        return new RetainerSale(
                row.getLong("id"),
                row.getLong("discord_user_id"),
                row.getString("retainer_name"),
                row.getInt("item_id"),
                row.getInt("world_id"),
                row.getBoolean("hq"),
                row.getLong("unit_price"),
                row.getInt("quantity"),
                row.get("sold_at", INSTANT_TIMESTAMP),
                row.getString("note"));
    }
}
