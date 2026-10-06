/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerPresetsIntegrationTest extends RepositoryTestBase {

    private PlannerPresets repo;

    @BeforeEach
    void setUp() {
        repo = new PlannerPresets();
        query("DELETE FROM planner_preset").single(call()).delete();
    }

    @Test
    void upsertCreatesThenRewritesInPlace() {
        var first = repo.upsertByName(1L, "quick flip", "{\"budget\":500000}");
        var second = repo.upsertByName(1L, "quick flip", "{\"budget\":700000}");
        assertEquals(first.id(), second.id(), "same name → same row");
        assertTrue(second.paramsJson().contains("700000"));
        assertEquals(1, repo.list(1L).size());
    }

    @Test
    void listIsPerUserOrderedNewestFirst() {
        repo.upsertByName(1L, "a", "{}");
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }
        repo.upsertByName(1L, "b", "{}");
        repo.upsertByName(2L, "shared", "{}");
        var mine = repo.list(1L);
        assertEquals(2, mine.size());
        assertEquals("b", mine.getFirst().name(), "newest first");
    }

    @Test
    void findRefusesToLeakBetweenUsers() {
        var p = repo.upsertByName(1L, "mine", "{}");
        assertTrue(repo.find(p.id(), 1L).isPresent());
        assertFalse(repo.find(p.id(), 999L).isPresent());
    }

    @Test
    void renameChangesNameAndBumpsUpdatedAt() {
        var p = repo.upsertByName(1L, "old", "{}");
        var renamed = repo.rename(p.id(), 1L, "new");
        assertEquals("new", renamed.name());
        assertEquals(p.id(), renamed.id());
    }

    @Test
    void deleteRefusesOtherUsersRow() {
        var p = repo.upsertByName(1L, "mine", "{}");
        assertFalse(repo.delete(p.id(), 999L));
        assertTrue(repo.find(p.id(), 1L).isPresent(), "row survives cross-user delete");
        assertTrue(repo.delete(p.id(), 1L));
        assertFalse(repo.find(p.id(), 1L).isPresent());
    }

    @Test
    void deleteRandomIdIsANoop() {
        assertFalse(repo.delete(UUID.randomUUID(), 1L));
    }
}
