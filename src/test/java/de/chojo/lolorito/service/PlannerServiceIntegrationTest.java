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
import de.chojo.lolorito.repository.Offers;
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
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerServiceIntegrationTest extends ServiceIntegrationTestBase {

    private PlannerService service;
    private MarketModels marketModels;

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
        query("""
                INSERT INTO listings_updated(world, item, updated) VALUES (:w, :i, :u)
                ON CONFLICT(world, item) DO UPDATE SET updated = excluded.updated
                """)
                .single(call().bind("w", worldId).bind("i", itemId).bind("u", Instant.now(), INSTANT_TIMESTAMP))
                .insert();
    }

    @BeforeEach
    void setUp() {
        marketModels = new MarketModels(dataSource);
        service = new PlannerService(
                new File(),
                new Offers(dataSource),
                NameSupplier.EMPTY,
                new ItemCatalog(),
                new Recipes(dataSource),
                new ItemDetail(),
                new DesynthResults(dataSource),
                marketModels);
        query("DELETE FROM listings").single(call()).delete();
        query("DELETE FROM listings_updated").single(call()).delete();
        query("DELETE FROM market_model").single(call()).delete();
        query("DELETE FROM recipe_ingredient").single(call()).delete();
        query("DELETE FROM recipe").single(call()).delete();
        query("DELETE FROM desynth_result").single(call()).delete();
        insertWorld(66, "Odin", 7, "Light", "Europe");
        insertWorld(402, "Alpha", 7, "Light", "Europe");
    }

    @Test
    void planProducesStopsForProfitableCandidates() {
        marketModels.upsert(new MarketModel(
                100,
                66,
                false,
                new PriceDistribution(6.9, 0.3, 100),
                new SaleRate(1.5, 1.0, 0.6),
                0.0,
                0.0,
                100,
                true,
                false,
                Instant.now()));
        // Big quantity so EV comfortably beats the same-DC hop cost.
        seedListing(402, 100, 100, 50, false);

        var params = PlannerService.paramsFor(
                new File(),
                66,
                7,
                // hop weight = 1 gil/sec — trivialise the trip cost for this test.
                new PlannerService.PlanRequest(
                        66, 6, 10_000_000L, 200, 24.0, 0.05, 1, 5, 250, null, null, null, null, null, null, null));
        var plan = service.plan(params, 6);
        assertEquals(1, plan.stops().size());
        assertEquals(402, plan.stops().getFirst().worldId());
    }

    @Test
    void planReturnsEmptyStopsWhenNoCandidates() {
        var params = PlannerService.paramsFor(
                new File(),
                66,
                7,
                new PlannerService.PlanRequest(
                        66, 6, 1_000_000L, 100, 8.0, 1.0, 500, 5, 250, null, null, null, null, null, null, null));
        var plan = service.plan(params, 6);
        assertTrue(plan.stops().isEmpty());
    }

    @Test
    void craftScanGatesOnClassLevelAndFallsBackWhenNoSkillsStored() {
        // Recipe: BSM level 80 crafting item 200 from item 10.
        query("INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty) VALUES (1, 200, 'BSM', 80, 1)")
                .single(call())
                .insert();
        query("INSERT INTO recipe_ingredient (recipe_id, item_id, quantity) VALUES (1, 10, 1)")
                .single(call())
                .insert();
        // Product sells ~1000 on the home world; ingredient buys at 100.
        marketModels.upsert(new MarketModel(
                200,
                66,
                false,
                new PriceDistribution(6.9, 0.3, 100),
                new SaleRate(1.5, 1.0, 0.6),
                0.0,
                0.0,
                100,
                true,
                false,
                Instant.now()));
        seedListing(66, 10, 100, 10, false);

        var params = PlannerService.paramsFor(
                new File(),
                66,
                7,
                new PlannerService.PlanRequest(
                        66, 6, 10_000_000L, 200, 24.0, 0.05, 1, 5, 250, null, null, null, null, null, null, null));

        // Level too low → gated out. Level sufficient → candidate. No
        // stored skills at all → fallback: candidate appears ungated.
        assertTrue(
                service.loadCraftCandidates(params, java.util.Map.of("BSM", 50)).isEmpty());
        assertEquals(
                1,
                service.loadCraftCandidates(params, java.util.Map.of("BSM", 80)).size());
        assertEquals(1, service.loadCraftCandidates(params, java.util.Map.of()).size());
        // null → crafts disabled entirely.
        assertTrue(service.loadCraftCandidates(params, null).isEmpty());
    }

    @Test
    void craftPicksLeaveStopsAndCarryBillOfMaterials() {
        query("INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty) VALUES (1, 200, 'BSM', 80, 1)")
                .single(call())
                .insert();
        query("INSERT INTO recipe_ingredient (recipe_id, item_id, quantity) VALUES (1, 10, 2)")
                .single(call())
                .insert();
        marketModels.upsert(new MarketModel(
                200,
                66,
                false,
                new PriceDistribution(6.9, 0.3, 100),
                new SaleRate(1.5, 1.0, 0.6),
                0.0,
                0.0,
                100,
                true,
                false,
                Instant.now()));
        // Ingredient is cheapest on the neighbour world — the BOM must say so.
        seedListing(402, 10, 100, 10, false);

        var params = PlannerService.paramsFor(
                new File(),
                66,
                7,
                new PlannerService.PlanRequest(
                        66, 6, 10_000_000L, 200, 24.0, 0.05, 1, 5, 250, null, null, null, null, null, null, null));
        var plan = service.plan(params, 6, java.util.Map.of("BSM", 80));

        // The craft is profitable, so it must appear — but as a craft
        // section entry, not as a buy line on any stop.
        assertEquals(1, plan.crafts().size());
        assertTrue(plan.stops().stream()
                .flatMap(s -> s.buys().stream())
                .noneMatch(b -> b.action() == de.chojo.lolorito.planner.PlanAction.CRAFT));

        var craft = plan.crafts().getFirst();
        assertEquals(200, craft.itemId());
        assertEquals("BSM", craft.craftClass());
        assertEquals(1, craft.materials().size());
        var material = craft.materials().getFirst();
        assertEquals(10, material.itemId());
        assertEquals(2, material.qty());
        assertEquals(402, material.worldId());
        assertEquals("Alpha", material.worldName());
        assertEquals(200L, material.totalCost());
        assertEquals(200L, craft.materialsCost());
        assertEquals(200L, plan.craftMaterialsCost());
        assertTrue(plan.craftEvGross() > 0);
        assertTrue(craft.valuation().evGross() > 0);

        // The materials fold into the route: Alpha gets a stop with a
        // MATERIAL line the player buys alongside any resale picks there.
        var alphaStop = plan.stops().stream()
                .filter(s -> s.worldId() == 402)
                .findFirst()
                .orElseThrow();
        var materialBuy = alphaStop.buys().stream()
                .filter(b -> b.action() == de.chojo.lolorito.planner.PlanAction.MATERIAL)
                .findFirst()
                .orElseThrow();
        assertEquals(10, materialBuy.itemId());
        assertEquals(2, materialBuy.qty());
        assertEquals(100, materialBuy.buyPrice());
        assertTrue(materialBuy.valuation() == null, "material lines carry no own valuation");
        assertEquals(200L, alphaStop.buyCost());
        // Plan totals count the materials (not the product pseudo-buy),
        // and the added stop's hops are charged.
        assertEquals(200L, plan.totalBuyCost());
        assertTrue(plan.totalHopSeconds() > 0);
    }

    @Test
    void desynthPicksLandOnStopsAndCarryOutputs() {
        // Source item 300 desynths into 2× component 20 (CRP L40).
        query("""
                INSERT INTO desynth_result (source_item_id, component_item_id, avg_qty, desynth_class, desynth_level)
                VALUES (300, 20, 2.0, 'CRP', 40)
                """).single(call()).insert();
        // Component sells ~1000 at home; source buys at 100 on Alpha.
        marketModels.upsert(new MarketModel(
                20,
                66,
                false,
                new PriceDistribution(6.9, 0.3, 100),
                new SaleRate(1.5, 1.0, 0.6),
                0.0,
                0.0,
                100,
                true,
                false,
                Instant.now()));
        seedListing(402, 300, 100, 10, false);

        var params = PlannerService.paramsFor(
                new File(),
                66,
                7,
                new PlannerService.PlanRequest(
                        66, 6, 10_000_000L, 200, 24.0, 0.05, 1, 5, 250, null, null, null, null, null, null, null));

        // Gate: level too low → nothing. Sufficient → synthesis.
        assertTrue(service.loadDesynthSyntheses(params, java.util.Map.of("CRP", 10))
                .isEmpty());
        assertTrue(service.loadDesynthSyntheses(params, null).isEmpty());
        assertEquals(
                1,
                service.loadDesynthSyntheses(params, java.util.Map.of("CRP", 40))
                        .size());

        var plan = service.plan(params, 6, null, java.util.Map.of("CRP", 40));
        assertEquals(1, plan.desynths().size());
        var desynth = plan.desynths().getFirst();
        assertEquals(300, desynth.itemId());
        assertEquals(402, desynth.sourceWorldId());
        assertEquals("CRP", desynth.desynthClass());
        assertEquals(1, desynth.outputs().size());
        assertEquals(20, desynth.outputs().getFirst().itemId());
        assertEquals(2.0, desynth.outputs().getFirst().avgQty(), 1e-9);
        assertTrue(desynth.valuation().evGross() > 0);
        assertEquals(plan.desynthBuyCost(), desynth.buyCost());
        assertTrue(plan.desynthEvGross() > 0);

        // The buy itself is an ordinary stop line on Alpha.
        var alphaStop = plan.stops().stream()
                .filter(s -> s.worldId() == 402)
                .findFirst()
                .orElseThrow();
        assertTrue(alphaStop.buys().stream()
                .anyMatch(b -> b.action() == de.chojo.lolorito.planner.PlanAction.DESYNTH && b.itemId() == 300));
    }

    @Test
    void desynthProbePrefersCheaperHqBoard() {
        query("""
                INSERT INTO desynth_result (source_item_id, component_item_id, avg_qty, desynth_class, desynth_level)
                VALUES (300, 20, 2.0, 'CRP', 40)
                """).single(call()).insert();
        marketModels.upsert(new MarketModel(
                20,
                66,
                false,
                new PriceDistribution(6.9, 0.3, 100),
                new SaleRate(1.5, 1.0, 0.6),
                0.0,
                0.0,
                100,
                true,
                false,
                Instant.now()));
        // The HQ board undercuts the NQ board — the probe must buy HQ.
        seedListing(402, 300, 100, 10, false);
        seedListing(402, 300, 60, 10, true);

        var params = PlannerService.paramsFor(
                new File(),
                66,
                7,
                new PlannerService.PlanRequest(
                        66, 6, 10_000_000L, 200, 24.0, 0.05, 1, 5, 250, null, null, null, null, null, null, null));
        var syntheses = service.loadDesynthSyntheses(params, java.util.Map.of("CRP", 40));
        assertEquals(1, syntheses.size());
        assertTrue(syntheses.getFirst().candidate().hq());
        assertTrue(syntheses.getFirst().desynth().hq());
        assertEquals(60, syntheses.getFirst().desynth().buyPrice());
    }

    @Test
    void solverChargesMaterialWorldHops() {
        query("INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty) VALUES (1, 200, 'BSM', 80, 1)")
                .single(call())
                .insert();
        query("INSERT INTO recipe_ingredient (recipe_id, item_id, quantity) VALUES (1, 10, 2)")
                .single(call())
                .insert();
        marketModels.upsert(new MarketModel(
                200,
                66,
                false,
                new PriceDistribution(6.9, 0.3, 100),
                new SaleRate(1.5, 1.0, 0.6),
                0.0,
                0.0,
                100,
                true,
                false,
                Instant.now()));
        seedListing(402, 10, 100, 10, false);

        var params = PlannerService.paramsFor(
                new File(),
                66,
                7,
                new PlannerService.PlanRequest(
                        66, 6, 10_000_000L, 200, 24.0, 0.05, 1, 5, 250, null, null, null, null, null, null, null));
        var plan = service.plan(params, 6, java.util.Map.of("BSM", 80));

        // Craft-only plan whose materials sit on Alpha: the objective must
        // pay for the out-and-back trip (hop weight 1 gil/s here), not
        // treat the shopping run as free.
        assertEquals(1, plan.crafts().size());
        double expectedHopCharge = 2.0 * params.tDcSeconds();
        assertEquals(plan.crafts().getFirst().valuation().evGross() - expectedHopCharge, plan.objective(), 1e-6);
    }

    @Test
    void sharedBookRepricesSecondCraftDeeper() {
        // Two recipes both need 2× item 10; the cheap level only holds 2
        // units at 100, the next costs 200. The first chosen craft shops
        // the front of the book, the second must pay the deeper price.
        query("INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty) VALUES (1, 200, 'BSM', 80, 1)")
                .single(call())
                .insert();
        query("INSERT INTO recipe_ingredient (recipe_id, item_id, quantity) VALUES (1, 10, 2)")
                .single(call())
                .insert();
        query("INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty) VALUES (2, 201, 'BSM', 80, 1)")
                .single(call())
                .insert();
        query("INSERT INTO recipe_ingredient (recipe_id, item_id, quantity) VALUES (2, 10, 2)")
                .single(call())
                .insert();
        for (int product : new int[] {200, 201}) {
            marketModels.upsert(new MarketModel(
                    product,
                    66,
                    false,
                    new PriceDistribution(6.9, 0.3, 100),
                    new SaleRate(1.5, 1.0, 0.6),
                    0.0,
                    0.0,
                    100,
                    true,
                    false,
                    Instant.now()));
        }
        seedListing(402, 10, 100, 2, false);
        seedListing(402, 10, 200, 99, false);

        var params = PlannerService.paramsFor(
                new File(),
                66,
                7,
                new PlannerService.PlanRequest(
                        66, 6, 10_000_000L, 200, 24.0, 0.05, 1, 5, 250, null, null, null, null, null, null, null));
        var plan = service.plan(params, 6, java.util.Map.of("BSM", 80));

        assertEquals(2, plan.crafts().size());
        var costs = plan.crafts().stream()
                .map(de.chojo.lolorito.planner.PlanCraft::materialsCost)
                .sorted()
                .toList();
        assertEquals(200L, costs.get(0), "first craft shops the cheap level");
        assertEquals(400L, costs.get(1), "second craft pays the deeper price");
        assertEquals(600L, plan.craftMaterialsCost());
        // The stop's material lines carry the shared-walk total too.
        var alphaStop = plan.stops().stream()
                .filter(st -> st.worldId() == 402)
                .findFirst()
                .orElseThrow();
        assertEquals(600L, alphaStop.buyCost());
    }

    @Test
    void replanKeepsLanesAndDropsCompletedWorlds() {
        query("""
                INSERT INTO desynth_result (source_item_id, component_item_id, avg_qty, desynth_class, desynth_level)
                VALUES (300, 20, 2.0, 'CRP', 40)
                """).single(call()).insert();
        marketModels.upsert(new MarketModel(
                20,
                66,
                false,
                new PriceDistribution(6.9, 0.3, 100),
                new SaleRate(1.5, 1.0, 0.6),
                0.0,
                0.0,
                100,
                true,
                false,
                Instant.now()));
        seedListing(402, 300, 100, 10, false);

        var params = PlannerService.paramsFor(
                new File(),
                66,
                7,
                new PlannerService.PlanRequest(
                        66, 6, 10_000_000L, 200, 24.0, 0.05, 1, 5, 250, null, null, null, null, null, null, null));

        // Lanes carry into the replan…
        var fresh = service.replan(
                params,
                6,
                new PlannerService.ReplanContext(java.util.Set.of(), 0L, 0),
                null,
                java.util.Map.of("CRP", 40));
        assertEquals(1, fresh.desynths().size());
        // …and completed worlds drop the lane's candidates like resale's.
        var afterAlpha = service.replan(
                params,
                6,
                new PlannerService.ReplanContext(java.util.Set.of(402), 0L, 0),
                null,
                java.util.Map.of("CRP", 40));
        assertTrue(afterAlpha.desynths().isEmpty());
    }

    @Test
    void planRejectsUnknownHomeWorld() {
        var params = PlannerService.paramsFor(
                new File(),
                Integer.MAX_VALUE,
                7,
                new PlannerService.PlanRequest(
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null));
        assertThrows(IllegalArgumentException.class, () -> service.plan(params, 6));
    }
}
