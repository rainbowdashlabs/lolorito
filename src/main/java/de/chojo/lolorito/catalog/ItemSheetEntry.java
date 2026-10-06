/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.catalog;

/**
 * One row of the XIVAPI Item sheet as consumed by
 * {@link IconCatalogBuilder}. Feeds {@code /item/icons.json},
 * {@code /item/items.json}, and {@code /item/stack-sizes.json} on disk,
 * plus the runtime {@link de.chojo.lolorito.service.ItemCatalog} maps.
 *
 * <p>{@code category} is the UI category name (e.g. "Crystal",
 * "Reagent"), {@code description} is the in-game flavour text. Both
 * can be empty when XIVAPI doesn't have them for the item.
 */
public record ItemSheetEntry(
        int id, int icon, int ilvl, int stackSize, String category, String description, boolean canBeHq) {

    /**
     * Legacy constructor for tests that only care about the numeric
     * fields — category / description default to empty strings.
     */
    public ItemSheetEntry(int id, int icon, int ilvl, int stackSize) {
        this(id, icon, ilvl, stackSize, "", "", false);
    }
}
