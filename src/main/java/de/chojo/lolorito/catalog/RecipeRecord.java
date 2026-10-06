/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.catalog;

import java.util.List;

/**
 * One recipe as consumed by the recipe seed. Shape matches the JSON
 * loaded by {@link de.chojo.lolorito.service.CraftDesynthLoader}, so
 * writing to disk and re-reading round-trips 1:1.
 */
public record RecipeRecord(
        int id, int productItemId, String craftClass, int level, int yield, List<Ingredient> ingredients) {

    public record Ingredient(int itemId, int quantity) {}
}
