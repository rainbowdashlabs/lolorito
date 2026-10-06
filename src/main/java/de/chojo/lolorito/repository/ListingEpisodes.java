/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * SQL access to {@code lolorito.listing_episode} + {@code
 * lolorito.undercut_event} (patch_30) — the listing-lifecycle primitive
 * behind undercut/ghost detection and shelf-time calibration.
 *
 * <p>Universalis sends the <em>full</em> current listing set per
 * (world, item), never diffs; {@code ListingDiffer} turns consecutive
 * snapshots into the open/closed episodes stored here.
 */
@Singleton
public class ListingEpisodes {
    @Inject
    public ListingEpisodes(DataSource dataSource) {
        // sadu 2 uses the default QueryConfiguration
    }

    /** One currently-open episode, as the differ needs it for matching. */
    public record OpenEpisode(long id, String identity, boolean hq, int unitPrice, int quantity, Instant firstSeen) {}

    public List<OpenEpisode> openFor(int world, int item) {
        return query("""
                SELECT id, identity, hq, unit_price, quantity, first_seen
                  FROM listing_episode
                 WHERE world = :world AND item = :item AND ended_at IS NULL
                """)
                .single(call().bind("world", world).bind("item", item))
                .map(row -> new OpenEpisode(
                        row.getLong("id"),
                        row.getString("identity"),
                        row.getBoolean("hq"),
                        row.getInt("unit_price"),
                        row.getInt("quantity"),
                        row.get("first_seen", INSTANT_TIMESTAMP)))
                .all();
    }

    /** A listing seen for the first time in a snapshot. */
    public record NewEpisode(String identity, boolean hq, int unitPrice, int quantity, String retainerId) {}

    public void open(int world, int item, Collection<NewEpisode> episodes, Instant now) {
        for (NewEpisode e : episodes) {
            query("""
                    INSERT INTO listing_episode
                        (world, item, hq, identity, unit_price, quantity, retainer_id, first_seen, last_seen)
                    VALUES (:world, :item, :hq, :identity, :price, :qty, :retainer, :now, :now)
                    ON CONFLICT (world, item, identity) WHERE ended_at IS NULL DO NOTHING
                    """)
                    .single(call().bind("world", world)
                            .bind("item", item)
                            .bind("hq", e.hq())
                            .bind("identity", e.identity())
                            .bind("price", e.unitPrice())
                            .bind("qty", e.quantity())
                            .bind("retainer", e.retainerId())
                            .bind("now", now, INSTANT_TIMESTAMP))
                    .insert();
        }
    }

    public void touch(Collection<Long> ids, Instant now) {
        if (ids.isEmpty()) return;
        query("UPDATE listing_episode SET last_seen = :now WHERE id IN (%s)".formatted(inList(ids)))
                .single(call().bind("now", now, INSTANT_TIMESTAMP))
                .update();
    }

    /**
     * Close episodes that vanished from the snapshot. A vanished listing
     * whose exact (hq, unit_price, quantity) matches a sale recorded since
     * it appeared counts as SOLD; everything else is DELISTED. Sales that
     * arrive <em>after</em> the close are healed by
     * {@link #reclassifySold(int, int, boolean, int, int, Instant)}.
     */
    public void close(int world, int item, Collection<Long> ids, Instant now) {
        if (ids.isEmpty()) return;
        query("""
                UPDATE listing_episode e
                   SET ended_at = :now,
                       end_reason = CASE WHEN EXISTS (
                               SELECT 1 FROM sales s
                                WHERE s.world = :world AND s.item = :item AND s.hq = e.hq
                                  AND s.unit_price = e.unit_price AND s.quantity = e.quantity
                                  AND s.sold >= e.first_seen)
                           THEN 'SOLD' ELSE 'DELISTED' END
                 WHERE e.id IN (%s)
                """.formatted(inList(ids)))
                .single(call().bind("now", now, INSTANT_TIMESTAMP)
                        .bind("world", world)
                        .bind("item", item))
                .update();
    }

    /**
     * Late-arriving sale event: flip a recently-DELISTED episode with the
     * same value tuple to SOLD. Bounded to a short horizon so an old
     * delist can't be claimed by an unrelated identical sale.
     */
    public void reclassifySold(int world, int item, boolean hq, int unitPrice, int quantity, Instant soldAt) {
        query("""
                UPDATE listing_episode
                   SET end_reason = 'SOLD'
                 WHERE id IN (
                     SELECT id FROM listing_episode
                      WHERE world = :world AND item = :item AND hq = :hq
                        AND unit_price = :price AND quantity = :qty
                        AND end_reason = 'DELISTED'
                        AND ended_at >= (:sold_at)::timestamp - INTERVAL '2 HOURS'
                      ORDER BY ended_at DESC
                      LIMIT 1)
                """)
                .single(call().bind("world", world)
                        .bind("item", item)
                        .bind("hq", hq)
                        .bind("price", unitPrice)
                        .bind("qty", quantity)
                        .bind("sold_at", soldAt, INSTANT_TIMESTAMP))
                .update();
    }

    public void recordUndercut(int world, int item, boolean hq, Instant at, int newPrice, int oldFloor) {
        query("""
                INSERT INTO undercut_event (world, item, hq, at, new_price, old_floor)
                VALUES (:world, :item, :hq, :at, :new_price, :old_floor)
                """)
                .single(call().bind("world", world)
                        .bind("item", item)
                        .bind("hq", hq)
                        .bind("at", at, INSTANT_TIMESTAMP)
                        .bind("new_price", newPrice)
                        .bind("old_floor", oldFloor))
                .insert();
    }

    /** Measured undercuts/hour for a key over the window — feeds {@code market_model.lambda_undercut}. */
    public double undercutsPerHour(int world, int item, boolean hq, int windowDays) {
        int days = Math.max(1, windowDays);
        int count = query("""
                SELECT count(*)::int c FROM undercut_event
                 WHERE world = :world AND item = :item AND hq = :hq
                   AND at >= now() - (:days || ' days')::INTERVAL
                """)
                .single(call().bind("world", world)
                        .bind("item", item)
                        .bind("hq", hq)
                        .bind("days", String.valueOf(days)))
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
        return count / (days * 24.0);
    }

    /**
     * Ghost share of the current open stack: units that have sat unsold
     * beyond the ghost window ÷ all open units. 0 when nothing is open.
     */
    public double ghostFraction(int world, int item, boolean hq, int ghostWindowDays) {
        return query("""
                SELECT coalesce(sum(quantity) FILTER (
                           WHERE first_seen < now() - (:days || ' days')::INTERVAL), 0)::float8 AS aged,
                       coalesce(sum(quantity), 0)::float8 AS total
                  FROM listing_episode
                 WHERE world = :world AND item = :item AND hq = :hq AND ended_at IS NULL
                """)
                .single(call().bind("world", world)
                        .bind("item", item)
                        .bind("hq", hq)
                        .bind("days", String.valueOf(Math.max(1, ghostWindowDays))))
                .map(row -> {
                    double total = row.getDouble("total");
                    return total <= 0 ? 0.0 : row.getDouble("aged") / total;
                })
                .first()
                .orElse(0.0);
    }

    /** One completed SOLD episode — predicted-vs-realised shelf-time calibration reads these. */
    public record SoldEpisode(
            int world, int item, boolean hq, int unitPrice, int quantity, Instant firstSeen, Instant endedAt) {}

    public List<SoldEpisode> recentSold(int windowDays, int limit) {
        return query("""
                SELECT world, item, hq, unit_price, quantity, first_seen, ended_at
                  FROM listing_episode
                 WHERE end_reason = 'SOLD'
                   AND ended_at >= now() - (:days || ' days')::INTERVAL
                 ORDER BY ended_at DESC
                 LIMIT :lim
                """)
                .single(call().bind("days", String.valueOf(Math.max(1, windowDays)))
                        .bind("lim", limit))
                .map(row -> new SoldEpisode(
                        row.getInt("world"),
                        row.getInt("item"),
                        row.getBoolean("hq"),
                        row.getInt("unit_price"),
                        row.getInt("quantity"),
                        row.get("first_seen", INSTANT_TIMESTAMP),
                        row.get("ended_at", INSTANT_TIMESTAMP)))
                .all();
    }

    /** Retention: drop ended episodes + undercut events past the horizon. Returns rows removed. */
    public int clean(int retentionDays) {
        String days = String.valueOf(Math.max(1, retentionDays));
        int episodes = query("""
                DELETE FROM listing_episode
                 WHERE ended_at IS NOT NULL AND ended_at < now() - (:days || ' days')::INTERVAL
                """).single(call().bind("days", days)).delete().rows();
        int undercuts = query("DELETE FROM undercut_event WHERE at < now() - (:days || ' days')::INTERVAL")
                .single(call().bind("days", days))
                .delete()
                .rows();
        return episodes + undercuts;
    }

    private static String inList(Collection<Long> ids) {
        // Always numeric — safe to inline; avoids sadu's array-binding limitations.
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
