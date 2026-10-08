/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ItemCatalog}'s classpath loader. Whether or not the
 * bundled {@code /item/stack-sizes.json} exists at test time we still
 * get a working catalog — the missing-file path is the fallback, the
 * present-file path is the happy one.
 */
class ItemCatalogTest {

    @Test
    void catalogAlwaysLoadsWithoutThrowing() {
        var catalog = new ItemCatalog();
        // Unknown items default to 999 (stackable), since most FFXIV mats
        // stack and under-counting slots is worse than over-counting.
        assertThat(catalog.stackSize(-1)).isEqualTo(999);
    }

    @Test
    void catalogReadsBundledStackSizes() {
        // The test resource at /item/stack-sizes.json seeds a handful of
        // known ids so we exercise the happy-path branch of load().
        var catalog = new ItemCatalog();
        assertThat(catalog.stackSize(5057)).isEqualTo(999);
        // Even a mapped id that resolves to 1 (non-stackable gear) is honoured.
        assertThat(catalog.stackSize(999_999)).isEqualTo(1);
        // Unknown ids fall back to the default (999).
        assertThat(catalog.stackSize(0)).isEqualTo(999);
    }

    @Test
    void twoInstancesRoundTripIndependently() {
        // Second construction runs load() again; keeps coverage on the
        // resource-read path even when the classpath is reordered.
        var a = new ItemCatalog();
        var b = new ItemCatalog();
        assertThat(a.stackSize(5057)).isEqualTo(b.stackSize(5057));
    }

    @Test
    void catalogReadsBundledItemsJson() {
        var catalog = new ItemCatalog();
        assertEquals(20001, catalog.iconIdFor(5057));
        assertEquals(710, catalog.ilvlFor(5057));
        assertEquals("Lumber", catalog.categoryFor(5057));
        assertEquals("Rough lumber.", catalog.descriptionFor(5057));
        assertTrue(catalog.canBeHq(5057));
        assertFalse(catalog.canBeHq(5058));
        assertEquals(
                new ItemCatalog.CatalogEntry(20001, 710, 999),
                catalog.allEntries().get(5057));
        assertEquals(
                new ItemCatalog.CatalogEntry(20002, 0, 999),
                catalog.allEntries().get(5058));
    }

    @Test
    void ingestMergesEntriesAndCountsOnlyNewStackSizes() {
        var catalog = new ItemCatalog();
        int before = catalog.size();
        var fresh = new de.chojo.lolorito.catalog.ItemSheetEntry(
                9_900_001, 4242, 99, 999, "Test Category", "A test item.", true);
        var bare = new de.chojo.lolorito.catalog.ItemSheetEntry(9_900_002, 0, 0, 0);
        var invalid = new de.chojo.lolorito.catalog.ItemSheetEntry(0, 1, 1, 1);

        int added = catalog.ingest(java.util.Map.of(1, fresh, 2, bare, 3, invalid));

        assertEquals(1, added);
        assertEquals(before + 1, catalog.size());
        assertEquals(999, catalog.stackSize(9_900_001));
        assertEquals(4242, catalog.iconIdFor(9_900_001));
        assertEquals(99, catalog.ilvlFor(9_900_001));
        assertEquals("Test Category", catalog.categoryFor(9_900_001));
        assertEquals("A test item.", catalog.descriptionFor(9_900_001));
        assertTrue(catalog.canBeHq(9_900_001), "refreshed entries carry their HQ flag");
        assertFalse(catalog.canBeHq(9_900_002));

        assertEquals(0, catalog.ingest(java.util.Map.of(1, fresh)), "re-ingesting a known stack size adds nothing");
        assertEquals(0, catalog.ingest(java.util.Map.of()));
        assertEquals(0, catalog.ingest(null));
    }
}
