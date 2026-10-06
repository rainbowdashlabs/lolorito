/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.List;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Read side for {@code lolorito.desynth_result} — components (and average
 * yield) produced by desynthing a given item.
 */
@Singleton
public class DesynthResults {
    @Inject
    public DesynthResults(DataSource dataSource) {
        // sadu 2 uses the default QueryConfiguration
    }

    public List<Component> findBySource(int sourceItemId) {
        return query("""
                SELECT component_item_id, avg_qty
                  FROM desynth_result
                 WHERE source_item_id = :src
                 ORDER BY avg_qty DESC
                """)
                .single(call().bind("src", sourceItemId))
                .map(row -> new Component(row.getInt("component_item_id"), row.getDouble("avg_qty")))
                .all();
    }

    /**
     * Every distinct source item id we know a desynth mapping for, with
     * the desynth class and level required (both nullable — Teamcraft-
     * fallback sources leave them unset). Used by the /desynth explorer
     * to filter candidates against the caller's skill levels.
     */
    public List<Source> allSources() {
        return query("""
                SELECT source_item_id,
                       MAX(desynth_class) AS desynth_class,
                       MAX(desynth_level) AS desynth_level
                  FROM desynth_result
                 GROUP BY source_item_id
                 ORDER BY source_item_id
                """)
                .single(call())
                .map(row -> new Source(row.getInt("source_item_id"), row.getString("desynth_class"), (Integer)
                        row.getObject("desynth_level")))
                .all();
    }

    /** Class + level required to desynth this source (both nullable). Used by the item detail card. */
    public java.util.Optional<Source> sourceOf(int sourceItemId) {
        return query("""
                SELECT source_item_id,
                       MAX(desynth_class) AS desynth_class,
                       MAX(desynth_level) AS desynth_level
                  FROM desynth_result
                 WHERE source_item_id = :src
                 GROUP BY source_item_id
                """)
                .single(call().bind("src", sourceItemId))
                .map(row -> new Source(row.getInt("source_item_id"), row.getString("desynth_class"), (Integer)
                        row.getObject("desynth_level")))
                .first();
    }

    public record Component(int componentItemId, double avgQty) {}

    /** Source with the (nullable) crafter class + level required to desynth it. */
    public record Source(int itemId, String desynthClass, Integer desynthLevel) {}
}
