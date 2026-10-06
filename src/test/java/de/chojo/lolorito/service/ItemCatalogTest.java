/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
