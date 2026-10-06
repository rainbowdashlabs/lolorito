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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterProfilesIntegrationTest extends RepositoryTestBase {

    private CharacterProfiles repo;

    @BeforeEach
    void setUp() {
        repo = new CharacterProfiles();
        query("DELETE FROM character_profile").single(call()).delete();
    }

    @Test
    void upsertCreatesThenReplacesInPlace() {
        var first = repo.upsert(1L, 42L, "{\"name\":\"Aria\"}");
        var second = repo.upsert(1L, 43L, "{\"name\":\"Nora\"}");
        assertEquals(second.lodestoneId(), 43L, "lodestone id rolls forward on upsert");
        assertTrue(second.profileJson().contains("Nora"));
        assertEquals(1L, first.discordUserId());
    }

    @Test
    void findReturnsEmptyWhenNoRow() {
        assertTrue(repo.find(999L).isEmpty());
    }

    @Test
    void deleteRemovesTheRow() {
        repo.upsert(1L, 42L, "{}");
        assertTrue(repo.delete(1L));
        assertTrue(repo.find(1L).isEmpty());
        assertFalse(repo.delete(1L), "second delete is a no-op");
    }
}
