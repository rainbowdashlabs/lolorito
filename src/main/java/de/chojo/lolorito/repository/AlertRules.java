/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Singleton;
import de.chojo.lolorito.entity.AlertKind;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.entity.AlertScope;
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
 * SQL for the {@code alert_rule} table. UUID binding via {@code UUID_STRING}
 * with {@code ::uuid} casts — same pattern as {@link Baskets}.
 */
@Singleton
public class AlertRules {

    public void insert(AlertRule rule) {
        query("""
                INSERT INTO alert_rule (id, user_id, item_id, world_id, data_center_id, hq,
                                        trigger_kind, threshold_price, enabled,
                                        cooldown_minutes, last_triggered_at, created_at)
                VALUES (:id::uuid, :user, :item, :world, :dc, :hq,
                        :kind, :threshold, :enabled,
                        :cooldown, :last, :created)
                """)
                .single(call().bind("id", rule.id(), UUID_STRING)
                        .bind("user", rule.userId())
                        .bind("item", rule.itemId())
                        .bind("world", rule.scope().worldId())
                        .bind("dc", rule.scope().dataCenterId())
                        .bind("hq", rule.hq())
                        .bind("kind", rule.kind().wire())
                        .bind("threshold", rule.thresholdPrice())
                        .bind("enabled", rule.enabled())
                        .bind("cooldown", rule.cooldownMinutes())
                        .bind("last", rule.lastTriggeredAt(), INSTANT_TIMESTAMP)
                        .bind("created", rule.createdAt(), INSTANT_TIMESTAMP))
                .insert();
    }

    public Optional<AlertRule> findById(UUID id) {
        return query("SELECT * FROM alert_rule WHERE id = :id::uuid")
                .single(call().bind("id", id, UUID_STRING))
                .map(AlertRules::read)
                .first();
    }

    public List<AlertRule> listByUser(long userId) {
        return query("""
                SELECT * FROM alert_rule
                 WHERE user_id = :u
                 ORDER BY created_at DESC
                """).single(call().bind("u", userId)).map(AlertRules::read).all();
    }

    /** Enabled rules the scanner should consider this pass. */
    public List<AlertRule> listEnabled() {
        return query("SELECT * FROM alert_rule WHERE enabled = TRUE")
                .single(call())
                .map(AlertRules::read)
                .all();
    }

    /** True iff a row was updated — used by the service to detect missing rows. */
    public boolean updateEnabled(UUID id, boolean enabled) {
        return query("UPDATE alert_rule SET enabled = :e WHERE id = :id::uuid")
                        .single(call().bind("e", enabled).bind("id", id, UUID_STRING))
                        .update()
                        .rows()
                > 0;
    }

    public void updateLastTriggered(UUID id, Instant when) {
        query("UPDATE alert_rule SET last_triggered_at = :t WHERE id = :id::uuid")
                .single(call().bind("t", when, INSTANT_TIMESTAMP).bind("id", id, UUID_STRING))
                .update();
    }

    public boolean delete(UUID id) {
        return query("DELETE FROM alert_rule WHERE id = :id::uuid")
                        .single(call().bind("id", id, UUID_STRING))
                        .delete()
                        .rows()
                > 0;
    }

    private static AlertRule read(Row row) throws SQLException {
        Integer worldId = (Integer) row.getObject("world_id");
        Integer dcId = (Integer) row.getObject("data_center_id");
        AlertScope scope = worldId != null ? AlertScope.forWorld(worldId) : AlertScope.forDataCenter(dcId);
        Boolean hq = (Boolean) row.getObject("hq");
        return new AlertRule(
                row.get("id", UUID_STRING),
                row.getLong("user_id"),
                row.getInt("item_id"),
                scope,
                hq,
                AlertKind.fromWire(row.getString("trigger_kind")),
                row.getInt("threshold_price"),
                row.getBoolean("enabled"),
                row.getInt("cooldown_minutes"),
                nullable(row, "last_triggered_at"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }

    private static Instant nullable(Row row, String col) throws SQLException {
        if (row.getObject(col) == null) return null;
        return row.get(col, INSTANT_TIMESTAMP);
    }
}
