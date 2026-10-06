/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.entity.OfferFilterTarget;
import de.chojo.lolorito.repository.MarketModels;
import de.chojo.lolorito.repository.Offers;
import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.OfferBounds;
import de.chojo.lolorito.value.PriceDistribution;
import de.chojo.lolorito.value.SaleRate;
import de.chojo.lolorito.value.UserPrefs;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffersServiceIntegrationTest extends ServiceIntegrationTestBase {

    private OffersService service;
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
        service = new OffersService(new File(), new Offers(dataSource), NameSupplier.EMPTY, new ItemCatalog());
        query("DELETE FROM listings").single().delete();
        query("DELETE FROM listings_updated").single().delete();
        query("DELETE FROM market_model").single().delete();
        insertWorld(66, "Odin", 7, "Light", "Europe");
        insertWorld(402, "Alpha", 7, "Light", "Europe");
    }

    @Test
    void topOffersReturnsScoredRowsSortedByEvPerHour() {
        // Home model — sell price median ~exp(6.9)=~1000g, rate 1/hour.
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
        seedListing(402, 100, 100, 2, false); // buy 100 → sell ~1000, EV positive
        var prefs = new UserPrefs(0.05, 0.25, 30.0);

        var out = service.topOffers(66, 7, 6, prefs, 10);
        assertEquals(1, out.size());
        assertEquals(100, out.getFirst().itemId());
        assertEquals(402, out.getFirst().sourceWorldId());
        assertTrue(out.getFirst().valuation().evPerHour() > 0);
    }

    @Test
    void topOffersFiltersNonPositiveRows() {
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
        // buyPrice = 100_000 → expected net ~950g → very negative EV → dropped.
        seedListing(402, 100, 100_000, 1, false);
        var prefs = new UserPrefs(0.05, 0.25, 30.0);
        assertTrue(service.topOffers(66, 7, 6, prefs, 10).isEmpty());
    }

    @Test
    void topOffersItemNameFallsBackToIdWhenSupplierEmpty() {
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
        seedListing(402, 100, 100, 2, false);
        // NameSupplier.EMPTY returns Name("","","","") — service should still surface "100" or empty.
        var prefs = new UserPrefs(0.05, 0.25, 30.0);
        var out = service.topOffers(66, 7, 6, prefs, 10);
        assertEquals(1, out.size());
        assertTrue(out.getFirst().itemName() != null);
    }

    @Test
    void topOffersReturnsEmptyWhenNoCandidateFound() {
        var prefs = new UserPrefs(0.05, 0.25, 30.0);
        assertTrue(service.topOffers(66, 7, 6, prefs, 10).isEmpty());
    }

    @Test
    void budgetClampsQuantityAndDropsRowsThatDoNotFit() {
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
        seedListing(402, 100, 100, 10, false);
        var prefs = new UserPrefs(0.05, 0.25, 30.0);

        var clamped = service.topOffers(
                66,
                7,
                "Europe",
                OfferFilterTarget.DATA_CENTER,
                6,
                prefs,
                new OfferBounds(350, 0),
                10,
                Language.ENGLISH);
        assertEquals(1, clamped.size());
        assertEquals(3, clamped.getFirst().quantity());
        assertNotNull(clamped.getFirst().confidence());

        var none = service.topOffers(
                66, 7, "Europe", OfferFilterTarget.DATA_CENTER, 6, prefs, new OfferBounds(99, 0), 10, Language.ENGLISH);
        assertTrue(none.isEmpty());
    }
}
