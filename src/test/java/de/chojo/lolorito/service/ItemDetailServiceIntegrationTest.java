/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.repository.DesynthResults;
import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.repository.MarketModels;
import de.chojo.lolorito.repository.Recipes;
import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.PriceDistribution;
import de.chojo.lolorito.value.SaleRate;
import de.chojo.universalis.provider.NameSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemDetailServiceIntegrationTest extends ServiceIntegrationTestBase {

    private ItemDetailService service;
    private MarketModels marketModels;

    private static MarketModel sufficient(int itemId, int worldId, boolean hq) {
        return new MarketModel(
                itemId,
                worldId,
                hq,
                new PriceDistribution(6.9, 0.3, 100),
                new SaleRate(1.5, 1.0, 0.6),
                0.0,
                0.0,
                100,
                true,
                false,
                Instant.now());
    }

    private static void seedListing(int worldId, int itemId, int unitPrice, int qty, boolean hq) {
        query("""
                INSERT INTO listings(world, item, hq, review_time, unit_price, quantity, total)
                VALUES (:w, :i, :hq, now(), :u, :q, :t)
                """)
                .single(call().bind("w", worldId)
                        .bind("i", itemId)
                        .bind("hq", hq)
                        .bind("u", unitPrice)
                        .bind("q", qty)
                        .bind("t", unitPrice * qty))
                .insert();
    }

    @BeforeEach
    void setUp() {
        marketModels = new MarketModels(dataSource);
        var file = new File();
        service = new ItemDetailService(
                file,
                new ItemDetail(),
                new Recipes(dataSource),
                new DesynthResults(dataSource),
                NameSupplier.EMPTY,
                new ItemCatalog(),
                new de.chojo.lolorito.value.MarketModelFitter(
                        file,
                        new de.chojo.lolorito.repository.MarketModelResiduals(null),
                        new de.chojo.lolorito.repository.ListingEpisodes(null)),
                marketModels);
        query("DELETE FROM listings").single(call()).delete();
        query("DELETE FROM market_model").single(call()).delete();
        query("DELETE FROM recipe_ingredient").single(call()).delete();
        query("DELETE FROM recipe").single(call()).delete();
        query("DELETE FROM desynth_result").single(call()).delete();
        insertWorld(66, "Odin", 7, "Light", "Europe");
        insertWorld(402, "Alpha", 7, "Light", "Europe");
    }

    @Test
    void loadReturnsBasicShapeWithoutRecipesOrDesynth() {
        marketModels.upsert(sufficient(100, 66, false));
        seedListing(66, 100, 500, 3, false);
        seedListing(402, 100, 300, 5, false);

        var detail = service.load(100, 66, false).orElseThrow();
        assertEquals(100, detail.itemId());
        assertEquals(66, detail.homeWorldId());
        assertEquals(2, detail.listings().size(), "both listings on Light DC");
        assertNotNull(detail.model());
        assertTrue(detail.crafts().isEmpty());
        // no desynth rows → breakdown null.
        assertTrue(detail.desynth() == null);
    }

    @Test
    void loadReturnsDesynthBreakdownWhenComponentsExist() {
        marketModels.upsert(sufficient(100, 66, false));
        marketModels.upsert(sufficient(200, 66, false));
        seedListing(66, 100, 500, 5, false);
        seedListing(402, 100, 300, 5, false);
        query("INSERT INTO desynth_result(source_item_id, component_item_id, avg_qty) VALUES (:s, :c, 0.5)")
                .single(call().bind("s", 100).bind("c", 200))
                .insert();

        var detail = service.load(100, 66, false).orElseThrow();
        assertNotNull(detail.desynth());
        assertEquals(1, detail.desynth().components().size());
        assertEquals(200, detail.desynth().components().getFirst().itemId());
        assertTrue(detail.desynth().components().getFirst().modelSufficient());
        // No class/level seeded → both surface as null (Teamcraft-fallback shape).
        assertNull(detail.desynth().desynthClass());
        assertNull(detail.desynth().desynthLevel());
    }

    @Test
    void loadReturnsDesynthClassAndLevelWhenSeeded() {
        marketModels.upsert(sufficient(100, 66, false));
        marketModels.upsert(sufficient(200, 66, false));
        seedListing(66, 100, 500, 5, false);
        seedListing(402, 100, 300, 5, false);
        query("""
                INSERT INTO desynth_result(source_item_id, component_item_id, avg_qty,
                                           desynth_class, desynth_level)
                VALUES (:s, :c, 0.5, 'armorer', 42)
                """).single(call().bind("s", 100).bind("c", 200)).insert();

        var detail = service.load(100, 66, false).orElseThrow();
        assertEquals("armorer", detail.desynth().desynthClass());
        assertEquals(42, detail.desynth().desynthLevel());
    }

    @Test
    void loadReturnsCraftBreakdownWhenRecipeExists() {
        marketModels.upsert(sufficient(500, 66, false));
        seedListing(66, 500, 10_000, 1, false);
        seedListing(402, 501, 100, 3, false); // ingredient
        query("""
                INSERT INTO recipe(id, product_item_id, craft_class, level, yield_qty)
                VALUES (1, 500, 'BSM', 30, 1)
                """).single(call()).insert();
        query("""
                INSERT INTO recipe_ingredient(recipe_id, item_id, quantity)
                VALUES (1, 501, 3)
                """).single(call()).insert();

        var detail = service.load(500, 66, false).orElseThrow();
        assertEquals(1, detail.crafts().size());
        var craft = detail.crafts().getFirst();
        assertEquals(1, craft.recipeId());
        assertEquals("BSM", craft.craftClass());
        assertEquals(1, craft.ingredients().size());
        assertEquals(501, craft.ingredients().getFirst().itemId());
        assertEquals(3, craft.ingredients().getFirst().quantity());
        assertEquals(100, craft.ingredients().getFirst().cheapestBuy());
        assertNotNull(craft.valuation());
    }

    @Test
    void loadReturnsEmptyWhenHomeWorldUnknown() {
        assertTrue(service.load(1, Integer.MAX_VALUE, false).isEmpty());
    }

    @Test
    void loadReturnsNullModelWhenMarketModelAbsent() {
        seedListing(66, 100, 500, 3, false);
        var detail = service.load(100, 66, false).orElseThrow();
        // model is null when there's no market_model row.
        assertTrue(detail.model() == null);
    }

    @Test
    void loadReturnsCraftWithNullValuationWhenIngredientPriceMissing() {
        marketModels.upsert(sufficient(500, 66, false));
        seedListing(66, 500, 10_000, 1, false);
        // ingredient 999 has NO listing anywhere → cheapestBuy = null → craft valuation null.
        query("""
                INSERT INTO recipe(id, product_item_id, craft_class, level, yield_qty)
                VALUES (2, 500, 'BSM', 30, 1)
                """).single(call()).insert();
        query("""
                INSERT INTO recipe_ingredient(recipe_id, item_id, quantity)
                VALUES (2, 999, 1)
                """).single(call()).insert();

        var detail = service.load(500, 66, false).orElseThrow();
        assertEquals(1, detail.crafts().size());
        assertTrue(detail.crafts().getFirst().valuation() == null);
        assertTrue(detail.crafts().getFirst().ingredients().getFirst().cheapestBuy() == null);
    }
}
