/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.PriceDistribution;
import de.chojo.lolorito.value.SaleRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffersIntegrationTest extends RepositoryTestBase {

    private Offers repo;
    private MarketModels marketModels;

    private static MarketModel sufficientModel(int worldId, int itemId, boolean hq) {
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

    private static void seedListingWithUpdated(
            int worldId, int itemId, int unitPrice, int qty, boolean hq, Instant updated) {
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
                ON CONFLICT (world, item) DO UPDATE SET updated = excluded.updated
                """)
                .single(call().bind("w", worldId).bind("i", itemId).bind("u", updated, INSTANT_TIMESTAMP))
                .insert();
    }

    @BeforeEach
    void setUp() {
        repo = new Offers(dataSource);
        marketModels = new MarketModels(dataSource);
        query("DELETE FROM listings").single(call()).delete();
        query("DELETE FROM listings_updated").single(call()).delete();
        query("DELETE FROM market_model").single(call()).delete();
        insertWorld(66, "Odin", 7, "Light", "Europe");
        insertWorld(402, "Alpha", 7, "Light", "Europe");
        insertWorld(97, "Ragnarok", 6, "Chaos", "Europe");
    }

    @Test
    void candidatesJoinListingsAgainstHomeModelAndDropStaleListings() {
        // Home world 66 (Light DC 7). Source ALPHA=402 (same DC), RAGNAROK=97 (Chaos).
        // Use separate items so listings_updated stamps don't overwrite each other.
        marketModels.upsert(sufficientModel(66, 100, false));
        marketModels.upsert(sufficientModel(66, 200, false));
        marketModels.upsert(sufficientModel(66, 300, false));
        seedListingWithUpdated(402, 100, 500, 3, false, Instant.now().minus(30, ChronoUnit.MINUTES));
        seedListingWithUpdated(97, 100, 400, 4, false, Instant.now().minus(30, ChronoUnit.MINUTES)); // wrong DC
        seedListingWithUpdated(402, 200, 300, 2, false, Instant.now().minus(48, ChronoUnit.HOURS)); // stale
        seedListingWithUpdated(66, 300, 200, 1, false, Instant.now()); // home world — excluded

        var out = repo.candidates(66, 7, "", de.chojo.lolorito.entity.OfferFilterTarget.DATA_CENTER, 6, 100);
        assertEquals(1, out.size(), "only same-DC, fresh, non-home listings survive");
        var one = out.getFirst();
        assertEquals(402, one.sourceWorldId());
        assertEquals(100, one.itemId());
        assertEquals(500, one.buyPrice());
        assertEquals(3, one.quantity());
        assertTrue(one.model().sufficient());
    }

    @Test
    void candidatesSkipsKeysWithoutSufficientModel() {
        // No market model at all — nothing joins.
        seedListingWithUpdated(402, 100, 500, 3, false, Instant.now());
        assertTrue(repo.candidates(66, 7, "", de.chojo.lolorito.entity.OfferFilterTarget.DATA_CENTER, 6, 100)
                .isEmpty());
    }

    @Test
    void candidatesSkipsInsufficientModel() {
        var insufficient = new MarketModel(
                100,
                66,
                false,
                new PriceDistribution(6.9, 0.3, 3),
                new SaleRate(1.5, 1.0, 0.6),
                0.0,
                0.0,
                3,
                false,
                false,
                Instant.now());
        marketModels.upsert(insufficient);
        seedListingWithUpdated(402, 100, 500, 3, false, Instant.now());
        assertTrue(
                repo.candidates(66, 7, "", de.chojo.lolorito.entity.OfferFilterTarget.DATA_CENTER, 6, 100)
                        .isEmpty(),
                "market_model.sufficient = false → no join");
    }

    @Test
    void candidatesRespectsCap() {
        marketModels.upsert(sufficientModel(66, 100, false));
        marketModels.upsert(sufficientModel(66, 101, false));
        seedListingWithUpdated(402, 100, 500, 3, false, Instant.now());
        seedListingWithUpdated(402, 101, 500, 3, false, Instant.now());
        var out = repo.candidates(66, 7, "", de.chojo.lolorito.entity.OfferFilterTarget.DATA_CENTER, 6, 1);
        assertEquals(1, out.size());
    }

    @Test
    void candidatesRegionScopeIncludesOtherDataCentersInSameRegion() {
        // Home world 66 (Light DC 7, Europe). Source RAGNAROK=97 is on Chaos DC 8 (Europe).
        // With DATA_CENTER scope the DC 8 listing is dropped; with REGION it survives.
        marketModels.upsert(sufficientModel(66, 100, false));
        seedListingWithUpdated(97, 100, 400, 4, false, Instant.now().minus(30, ChronoUnit.MINUTES));

        var dc = repo.candidates(66, 7, "Europe", de.chojo.lolorito.entity.OfferFilterTarget.DATA_CENTER, 6, 100);
        assertTrue(dc.isEmpty(), "DATA_CENTER scope excludes other DCs even in the same region");

        var region = repo.candidates(66, 7, "Europe", de.chojo.lolorito.entity.OfferFilterTarget.REGION, 6, 100);
        assertEquals(1, region.size(), "REGION scope pulls in the same-region other-DC listing");
        assertEquals(97, region.getFirst().sourceWorldId());
    }
}
