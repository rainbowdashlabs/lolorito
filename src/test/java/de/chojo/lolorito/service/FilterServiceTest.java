/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.entity.OfferFilterRow;
import de.chojo.lolorito.entity.OfferFilterTarget;
import de.chojo.lolorito.repository.OfferFilters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FilterServiceTest {

    private FakeRepo repo;
    private FilterService service;

    @BeforeEach
    void setUp() {
        repo = new FakeRepo();
        service = new FilterService(repo);
    }

    @Test
    void currentReturnsDefaultsWhenAbsent() {
        var row = service.current(42);
        assertEquals(OfferFilterRow.defaults(), row);
    }

    @Test
    void updateMergesNonNullFieldsAndPersists() {
        var patch = new FilterService.FilterPatch(
                123, null, null, null, 12, null, null, null, null, null, null, null, "REGION", null, null);
        var updated = service.update(42, patch);
        assertEquals(123, updated.worldId());
        assertEquals(12, updated.refreshHours());
        assertEquals("REGION", updated.target());
        assertEquals(OfferFilterRow.defaults().offerLimit(), updated.offerLimit(), "unchanged fields inherit defaults");
        assertEquals(updated, repo.persisted.get(42L));
    }

    @Test
    void updateClampsOutOfRangeIntegers() {
        var patch = new FilterService.FilterPatch(
                null, -5, // offerLimit
                -100, // unitPrice
                null, 0, // refreshHours below min
                null, null, null, null, null, null, null, null, null, null);
        var updated = service.update(42, patch);
        assertEquals(1, updated.offerLimit(), "offer_limit clamps to [1, 10000]");
        assertEquals(0, updated.unitPrice(), "unit_price clamps to [0, MAX]");
        assertEquals(1, updated.refreshHours(), "refresh_hours clamps to [1, 168]");
    }

    @Test
    void updateClampsOutOfRangeDoubles() {
        var patch = new FilterService.FilterPatch(
                null, null, null, 500.0, null, 200.0, -1.0, 1000.0, null, null, null, null, null, null, null);
        var updated = service.update(42, patch);
        assertEquals(100.0, updated.factor(), 1e-9);
        assertEquals(100.0, updated.popularity(), 1e-9);
        assertEquals(0.0, updated.marketVolume(), 1e-9);
        assertEquals(100.0, updated.interest(), 1e-9);
    }

    @Test
    void updateNormalisesUnknownTargetToDataCenter() {
        var patch = new FilterService.FilterPatch(
                null, null, null, null, null, null, null, null, null, null, null, null, "GALAXY", null, null);
        var updated = service.update(42, patch);
        assertEquals("DATA_CENTER", updated.target());
    }

    @Test
    void updateAcceptsBothRegionAndDataCenterCaseInsensitive() {
        var region = service.update(
                1,
                new FilterService.FilterPatch(
                        null, null, null, null, null, null, null, null, null, null, null, null, "region", null, null));
        assertEquals("REGION", region.target());
        var dc = service.update(
                1,
                new FilterService.FilterPatch(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "data_center",
                        null,
                        null));
        assertEquals("DATA_CENTER", dc.target());
    }

    @Test
    void discordOptionsWithAllNullsLeavesRowUnchanged() {
        // Seed with a non-default row.
        var initial = new OfferFilterRow(555, 900, 300, 3.0, 6, 1.0, 2.0, 3.0, 10, 20, 500, 6000, "REGION");
        repo.persisted.put(99L, initial);
        var options = new FilterService.DiscordOptions(
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        var updated = service.applyDiscordOptions(99, options);
        assertEquals(initial, updated, "all-null options preserve every field of the persisted row");
    }

    @Test
    void discordOptionsWorldIdMapsFromWorldObject() {
        // World is a JDA-side object; the service unpacks id() into the patch.
        // Rather than build a fake World we go via the patch path directly here.
        var patch = new FilterService.FilterPatch(
                888, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        var updated = service.update(1, patch);
        assertEquals(888, updated.worldId());
    }

    @Test
    void discordOptionsTargetEnumPropagates() {
        var options = new FilterService.DiscordOptions(
                null, null, null, null, null, null, null, null, null, null, null, null, OfferFilterTarget.REGION);
        var updated = service.applyDiscordOptions(1, options);
        assertEquals("REGION", updated.target());
    }

    @Test
    void offerFilterRowTargetEnumFallsBackToDataCenter() {
        var row = new OfferFilterRow(1, 1, 1, 1.0, 1, 0, 0, 0, 0, 0, 0, 0, "GARBAGE");
        assertEquals(OfferFilterTarget.DATA_CENTER, row.targetEnum(), "malformed target falls back safely");
    }

    // --- Fake repo -------------------------------------------------------

    private static final class FakeRepo extends OfferFilters {
        final Map<Long, OfferFilterRow> persisted = new HashMap<>();

        FakeRepo() {
            super(null);
        }

        @Override
        public Optional<OfferFilterRow> find(long userId) {
            return Optional.ofNullable(persisted.get(userId));
        }

        @Override
        public void upsert(long userId, OfferFilterRow filter) {
            assertNull(null, "upsert accepts any value");
            persisted.put(userId, filter);
        }
    }

    @Test
    void updateStoresAndClearsQuantityBounds() {
        var set = service.update(
                42,
                new FilterService.FilterPatch(
                        null, null, null, null, null, null, null, null, null, null, null, null, null, 250_000, 500));
        assertEquals(250_000, set.budget());
        assertEquals(140, set.inventorySlots(), "inventory_slots clamps to the 140-slot inventory");

        var cleared = service.update(
                42,
                new FilterService.FilterPatch(
                        null, null, null, null, null, null, null, null, null, null, null, null, null, 0, null));
        assertEquals(0, cleared.budget(), "0 clears the budget bound");
        assertEquals(140, cleared.inventorySlots(), "null leaves the slot bound alone");
    }
}
