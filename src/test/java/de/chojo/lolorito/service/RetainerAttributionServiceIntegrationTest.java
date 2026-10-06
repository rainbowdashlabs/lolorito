/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.repository.CharacterRetainers;
import de.chojo.lolorito.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetainerAttributionServiceIntegrationTest extends RepositoryTestBase {

    private RetainerAttributionService service;
    private CharacterRetainers repo;

    @BeforeEach
    void setUp() {
        repo = new CharacterRetainers();
        service = new RetainerAttributionService(repo);
        query("DELETE FROM listings").single(call()).delete();
        query("DELETE FROM character_retainer").single(call()).delete();
        insertWorld(66, "Odin", 7, "Light", "Europe");
    }

    @Test
    void ownedListingsMatchesByWorldAndRetainerName() {
        service.put(1L, 66, "Wolfe");
        service.put(1L, 66, "Pupper");
        seedListing(66, 100, "Wolfe");
        seedListing(66, 101, "Pupper");
        seedListing(66, 999, "Unknown"); // not declared
        var owned = service.ownedListings(1L);
        assertEquals(2, owned.size());
        assertTrue(owned.stream().anyMatch(l -> "Wolfe".equals(l.retainerName())));
        assertTrue(owned.stream().anyMatch(l -> "Pupper".equals(l.retainerName())));
    }

    @Test
    void ownedListingsIsEmptyWhenNoRetainersDeclared() {
        seedListing(66, 100, "Wolfe");
        assertTrue(service.ownedListings(1L).isEmpty());
    }

    @Test
    void hasBeenSeenReturnsTrueOnlyForRetainersWithAListing() {
        service.put(1L, 66, "Wolfe");
        service.put(1L, 66, "Pupper");
        seedListing(66, 100, "Wolfe");
        assertTrue(service.hasBeenSeen(66, "Wolfe"));
        assertFalse(service.hasBeenSeen(66, "Pupper"));
    }

    @Test
    void undeclareRemovesTheRetainer() {
        service.put(1L, 66, "Wolfe");
        assertTrue(service.remove(1L, 66, "Wolfe"));
        assertEquals(0, repo.list(1L).size());
    }

    private static void seedListing(int worldId, int itemId, String retainerName) {
        query("""
                INSERT INTO listings(world, item, hq, review_time, unit_price, quantity, total, retainer_name)
                VALUES (:w, :i, false, now(), 100, 1, 100, :n)
                """)
                .single(call().bind("w", worldId).bind("i", itemId).bind("n", retainerName))
                .insert();
    }
}
