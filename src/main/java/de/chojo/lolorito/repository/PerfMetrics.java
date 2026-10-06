/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Singleton;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Cheap append-only telemetry the SPA dashboard uses to answer "is this
 * app healthy?". Every worker that wants to record its own timings calls
 * {@link #record(String, long, String)} once per cycle. Aggregates are
 * pulled straight from the table — no rollup tables yet.
 */
@Singleton
public class PerfMetrics {

    public void record(String metric, long valueMs, String detail) {
        query("""
                INSERT INTO perf_metric (captured_at, metric, value_ms, detail)
                VALUES (:t, :m, :v, :d)
                """)
                .single(call().bind("t", Instant.now(), INSTANT_TIMESTAMP)
                        .bind("m", metric)
                        .bind("v", valueMs)
                        .bind("d", detail))
                .insert();
    }

    /** Most recent N rows for the given metric, newest first. */
    public List<Row> recent(String metric, int limit) {
        return query("""
                SELECT captured_at, value_ms, detail
                  FROM perf_metric
                 WHERE metric = :m
                 ORDER BY captured_at DESC
                 LIMIT :lim
                """)
                .single(call().bind("m", metric).bind("lim", Math.max(1, limit)))
                .map(row -> new Row(
                        row.get("captured_at", INSTANT_TIMESTAMP), row.getLong("value_ms"), row.getString("detail")))
                .all();
    }

    public long databaseSizeBytes() {
        return query("SELECT pg_database_size(current_database()) AS bytes")
                .single(call())
                .map(row -> row.getLong("bytes"))
                .first()
                .orElse(0L);
    }

    public record Row(Instant capturedAt, long valueMs, String detail) {}
}
