/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.catalog;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic slice of {@link DesynthCatalogBuilder}. The XIVAPI +
 * Teamcraft paths are integration-shaped and stay untested here; these
 * cover the pure-function slice a reviewer can reason about locally.
 */
class DesynthCatalogBuilderTest {

    /** Crystal ingredients drop guaranteed; non-crystals share one drop across the first four. */
    @Test
    void deriveFromRecipesEmitsCrystalGuaranteedPlusMaterialShare() {
        var builder = new DesynthCatalogBuilder(new XivapiClient());
        var recipe = new RecipeRecord(
                1,
                100,
                "carpenter",
                50,
                1,
                List.of(
                        new RecipeRecord.Ingredient(2, 4), // crystal
                        new RecipeRecord.Ingredient(500, 1),
                        new RecipeRecord.Ingredient(501, 1),
                        new RecipeRecord.Ingredient(502, 1),
                        new RecipeRecord.Ingredient(503, 1),
                        new RecipeRecord.Ingredient(504, 1))); // 5th material — dropped

        var out = builder.deriveFromRecipes(List.of(recipe));

        assertThat(out).hasSize(1);
        var row = out.get(0);
        assertThat(row.sourceItemId()).isEqualTo(100);
        assertThat(row.desynthClass()).isEqualTo("carpenter");
        assertThat(row.desynthLevel()).isEqualTo(50);
        // Crystal at full qty (4), materials each at 1 / 4 = 0.25.
        assertThat(row.components()).anySatisfy(c -> {
            assertThat(c.componentItemId()).isEqualTo(2);
            assertThat(c.avgQty()).isEqualTo(4.0);
        });
        // Only the first four non-crystals survive, each at share=0.25.
        assertThat(row.components()).hasSize(1 + 4);
        long shared = row.components().stream()
                .filter(c -> c.componentItemId() != 2)
                .filter(c -> c.avgQty() == 0.25)
                .count();
        assertThat(shared).isEqualTo(4);
    }

    /**
     * Multi-recipe products lock the (class, level) gate to the lowest
     * recipe level and merge components by max avg_qty.
     */
    @Test
    void multipleRecipesForOneProductPickLowestLevelGate() {
        var builder = new DesynthCatalogBuilder(new XivapiClient());
        var lo = new RecipeRecord(1, 999, "carpenter", 20, 1, List.of(new RecipeRecord.Ingredient(500, 1)));
        var hi = new RecipeRecord(2, 999, "blacksmith", 60, 1, List.of(new RecipeRecord.Ingredient(500, 1)));

        var out = builder.deriveFromRecipes(List.of(lo, hi));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).desynthClass()).isEqualTo("carpenter");
        assertThat(out.get(0).desynthLevel()).isEqualTo(20);
    }

    /** Teamcraft's community map ships as {@code "sourceId": [compId, ...]}. */
    @Test
    void parseTeamcraftBuildsUniformYieldComponents() {
        var mapper = JsonMapper.builder().build();
        var tree = mapper.readTree("{\"5000\": [1, 2, 3, 4], \"junk\": [1], \"0\": [1], \"6000\": []}");
        var out = DesynthCatalogBuilder.parseTeamcraft(tree);

        assertThat(out).hasSize(1);
        var row = out.get(0);
        assertThat(row.sourceItemId()).isEqualTo(5000);
        assertThat(row.desynthClass()).isNull();
        assertThat(row.desynthLevel()).isNull();
        assertThat(row.components()).hasSize(4);
        assertThat(row.components()).allMatch(c -> c.avgQty() == 0.25);
    }

    /** Recipes win on collisions; Teamcraft fills the gaps. */
    @Test
    void mergeLayersPrefersRecipeDerivedRows() {
        var recipe = new DesynthSource(100, "carpenter", 10, List.of(new DesynthSource.Component(200, 1.0)));
        var teamcraft = new DesynthSource(100, null, null, List.of(new DesynthSource.Component(999, 1.0)));
        var teamcraftOnly = new DesynthSource(200, null, null, List.of(new DesynthSource.Component(300, 0.5)));

        var out = DesynthCatalogBuilder.mergeLayers(List.of(recipe), List.of(teamcraft, teamcraftOnly));

        assertThat(out).hasSize(2);
        assertThat(out.get(0).sourceItemId()).isEqualTo(100);
        assertThat(out.get(0).desynthClass()).isEqualTo("carpenter");
        assertThat(out.get(0).components().get(0).componentItemId()).isEqualTo(200);
        assertThat(out.get(1).sourceItemId()).isEqualTo(200);
        assertThat(out.get(1).desynthClass()).isNull();
    }
}
