/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Singleton;
import de.chojo.lolorito.entity.Basket;
import de.chojo.lolorito.entity.BasketItem;
import de.chojo.lolorito.entity.BasketVisibility;
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

/**
 * Raw SQL for the {@code basket} + {@code basket_item} tables. UUIDs are
 * bound via {@link de.chojo.sadu.queries.converter.StandardValueConverter#UUID_STRING},
 * which sends them as strings — Postgres needs the {@code ::uuid} cast in
 * the query for the comparison to match the {@code uuid} column type.
 */
@Singleton
public class Baskets {
    private static Head readHead(Row row) throws SQLException {
        return new Head(
                row.get("id", UUID_STRING),
                row.getLong("owner_user_id"),
                row.getString("share_token"),
                row.getString("name"),
                BasketVisibility.fromWire(row.getString("visibility")),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }

    /** Insert a fresh basket + its items in one round-trip. Caller owns id, shareToken, timestamps. */
    public void insert(Basket basket) {
        query("""
                INSERT INTO basket (id, owner_user_id, share_token, name, visibility, created_at, updated_at)
                VALUES (:id::uuid, :owner, :token, :name, :vis, :created, :updated)
                """)
                .single(call().bind("id", basket.id(), UUID_STRING)
                        .bind("owner", basket.ownerUserId())
                        .bind("token", basket.shareToken())
                        .bind("name", basket.name())
                        .bind("vis", basket.visibility().wire())
                        .bind("created", basket.createdAt(), INSTANT_TIMESTAMP)
                        .bind("updated", basket.updatedAt(), INSTANT_TIMESTAMP))
                .insert();
        insertItems(basket.id(), basket.items());
    }

    /** Replace the basket's items and bump {@code updated_at}. Does not touch owner / share token / visibility. */
    public void replaceItems(UUID basketId, List<BasketItem> items, Instant now) {
        query("DELETE FROM basket_item WHERE basket_id = :id::uuid")
                .single(call().bind("id", basketId, UUID_STRING))
                .delete();
        insertItems(basketId, items);
        query("UPDATE basket SET updated_at = :now WHERE id = :id::uuid")
                .single(call().bind("now", now, INSTANT_TIMESTAMP).bind("id", basketId, UUID_STRING))
                .update();
    }

    /** Rename / re-visibility. Returns {@code true} iff the row exists. */
    public boolean updateMeta(UUID basketId, String name, BasketVisibility visibility, Instant now) {
        return query("""
                UPDATE basket
                   SET name       = :name,
                       visibility = :vis,
                       updated_at = :now
                 WHERE id = :id::uuid
                """)
                        .single(call().bind("name", name)
                                .bind("vis", visibility.wire())
                                .bind("now", now, INSTANT_TIMESTAMP)
                                .bind("id", basketId, UUID_STRING))
                        .update()
                        .rows()
                > 0;
    }

    public boolean delete(UUID basketId) {
        return query("DELETE FROM basket WHERE id = :id::uuid")
                        .single(call().bind("id", basketId, UUID_STRING))
                        .delete()
                        .rows()
                > 0;
    }

    public Optional<Basket> findById(UUID basketId) {
        return findHead(basketId).map(this::hydrate);
    }

    public Optional<Basket> findByShareToken(String shareToken) {
        return query("SELECT * FROM basket WHERE share_token = :t")
                .single(call().bind("t", shareToken))
                .map(Baskets::readHead)
                .first()
                .map(this::hydrate);
    }

    public List<Basket> listByOwner(long ownerUserId) {
        var heads = query("""
                SELECT * FROM basket WHERE owner_user_id = :o
                 ORDER BY updated_at DESC
                """)
                .single(call().bind("o", ownerUserId))
                .map(Baskets::readHead)
                .all();
        return heads.stream().map(this::hydrate).toList();
    }

    private Optional<Head> findHead(UUID id) {
        return query("SELECT * FROM basket WHERE id = :id::uuid")
                .single(call().bind("id", id, UUID_STRING))
                .map(Baskets::readHead)
                .first();
    }

    private Basket hydrate(Head h) {
        return new Basket(
                h.id, h.ownerUserId, h.shareToken, h.name, h.visibility, h.createdAt, h.updatedAt, loadItems(h.id));
    }

    private List<BasketItem> loadItems(UUID basketId) {
        return query("""
                SELECT key, item_id, item_name, hq, source_world_id, source_world_name,
                       quantity, buy_price, action, ev_per_hour, added_at, position
                  FROM basket_item
                 WHERE basket_id = :id::uuid
                 ORDER BY position ASC
                """)
                .single(call().bind("id", basketId, UUID_STRING))
                .map(row -> new BasketItem(
                        row.getString("key"),
                        row.getInt("item_id"),
                        row.getString("item_name"),
                        row.getBoolean("hq"),
                        row.getInt("source_world_id"),
                        row.getString("source_world_name"),
                        row.getInt("quantity"),
                        row.getInt("buy_price"),
                        row.getString("action"),
                        row.getDouble("ev_per_hour"),
                        row.get("added_at", INSTANT_TIMESTAMP),
                        row.getInt("position")))
                .all();
    }

    private void insertItems(UUID basketId, List<BasketItem> items) {
        int position = 0;
        for (BasketItem item : items) {
            query("""
                    INSERT INTO basket_item (basket_id, key, item_id, item_name, hq,
                                             source_world_id, source_world_name, quantity, buy_price,
                                             action, ev_per_hour, added_at, position)
                    VALUES (:basket::uuid, :key, :item, :name, :hq,
                            :world, :worldName, :qty, :buy,
                            :action, :ev, :added, :pos)
                    """)
                    .single(call().bind("basket", basketId, UUID_STRING)
                            .bind("key", item.key())
                            .bind("item", item.itemId())
                            .bind("name", item.itemName())
                            .bind("hq", item.hq())
                            .bind("world", item.sourceWorldId())
                            .bind("worldName", item.sourceWorldName())
                            .bind("qty", item.quantity())
                            .bind("buy", item.buyPrice())
                            .bind("action", item.action())
                            .bind("ev", item.evPerHour())
                            .bind("added", item.addedAt(), INSTANT_TIMESTAMP)
                            .bind("pos", position++))
                    .insert();
        }
    }

    private record Head(
            UUID id,
            long ownerUserId,
            String shareToken,
            String name,
            BasketVisibility visibility,
            Instant createdAt,
            Instant updatedAt) {}
}
