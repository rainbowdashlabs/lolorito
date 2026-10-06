/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesynthResultsIntegrationTest extends RepositoryTestBase {

    private DesynthResults repo;

    private static void seed(int src, int component, double qty) {
        query("""
                INSERT INTO desynth_result (source_item_id, component_item_id, avg_qty)
                VALUES (:s, :c, :q)
                """)
                .single(call().bind("s", src).bind("c", component).bind("q", qty))
                .insert();
    }

    @BeforeEach
    void setUp() {
        repo = new DesynthResults(dataSource);
        query("DELETE FROM desynth_result").single(call()).delete();
    }

    @Test
    void findBySourceReturnsAllComponentsOrderedByYield() {
        seed(100, 10, 0.3);
        seed(100, 11, 0.9);
        seed(100, 12, 0.6);
        var out = repo.findBySource(100);
        assertEquals(3, out.size());
        assertEquals(11, out.get(0).componentItemId(), "highest avg_qty first");
        assertEquals(12, out.get(1).componentItemId());
        assertEquals(10, out.get(2).componentItemId());
    }

    @Test
    void findBySourceReturnsEmptyWhenNoRow() {
        assertTrue(repo.findBySource(999).isEmpty());
    }

    @Test
    void findBySourceIgnoresOtherSources() {
        seed(100, 10, 1.0);
        seed(200, 20, 1.0);
        var out = repo.findBySource(100);
        assertEquals(1, out.size());
        assertEquals(10, out.getFirst().componentItemId());
    }
}
