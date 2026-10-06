/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.repository.ListingEpisodes;
import de.chojo.lolorito.repository.MarketModelResiduals;
import de.chojo.lolorito.repository.MarketModels;
import de.chojo.lolorito.repository.PerfMetrics;
import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.PriceDistribution;
import de.chojo.lolorito.value.SaleRate;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationServiceDrilldownTest {

    private static final Instant T0 = Instant.parse("2026-07-01T00:00:00Z");

    private static MarketModel model(int itemId, boolean sufficient, double rate) {
        return new MarketModel(
                itemId,
                66,
                false,
                new PriceDistribution(Math.log(1000), 0.1, 50),
                new SaleRate(rate, rate, rate),
                0,
                0,
                50,
                sufficient,
                false,
                T0);
    }

    private static ListingEpisodes.SoldEpisode sold(int itemId, int qty, double hoursOnShelf) {
        return new ListingEpisodes.SoldEpisode(
                66, itemId, false, 1000, qty, T0, T0.plus(Duration.ofMinutes((long) (hoursOnShelf * 60))));
    }

    private static CalibrationService service(
            List<ListingEpisodes.SoldEpisode> episodes,
            Map<Integer, MarketModel> models,
            List<MarketModelResiduals.KeySummary> worst) {
        return new CalibrationService(
                new StubResiduals(worst),
                new PerfMetrics(),
                new StubEpisodes(episodes),
                new StubModels(models),
                TestNames.of(Map.of(100, TestNames.english("Iron Ore", "Eisenerz"))));
    }

    @Test
    void shelfTimeScoresEpisodesAgainstTheirModels() {
        var episodes = List.of(sold(100, 2, 4.0), sold(101, 1, 1.0), sold(102, 1, 1.0));
        var models = Map.of(100, model(100, true, 1.0), 101, model(101, false, 1.0));
        var snap = service(episodes, models, List.of()).shelfTime(14, 500);

        assertEquals(3, snap.soldEpisodes());
        assertEquals(1, snap.scoredCount(), "only the key with a sufficient model is scored");
        assertEquals(Math.log(2.0), snap.logRatioMean(), 1e-9, "4 h realised vs 2 h predicted");
        assertTrue(snap.interpretation().contains("2.0× longer"), snap.interpretation());
    }

    @Test
    void shelfTimeSkipsZeroRatesAndInstantSales() {
        var episodes = List.of(sold(100, 1, 0.0), sold(101, 1, 3.0));
        var models = Map.of(100, model(100, true, 1.0), 101, model(101, true, 0.0));
        var snap = service(episodes, models, List.of()).shelfTime(14, 500);
        assertEquals(0, snap.scoredCount());
        assertTrue(snap.interpretation().startsWith("No sold listing episodes"), snap.interpretation());
    }

    @Test
    void shelfTimeSpreadNeedsTwoEpisodes() {
        var episodes = List.of(sold(100, 1, 0.5), sold(100, 1, 0.5));
        var snap =
                service(episodes, Map.of(100, model(100, true, 1.0)), List.of()).shelfTime(14, 500);
        assertEquals(2, snap.scoredCount());
        assertEquals(0.0, snap.logRatioSigma(), 1e-9);
        assertTrue(snap.interpretation().contains("faster"), snap.interpretation());
    }

    @Test
    void interpretShelfCoversEveryBand() {
        assertTrue(CalibrationService.interpretShelf(0, 0).startsWith("No sold"));
        assertTrue(CalibrationService.interpretShelf(5, Math.log(1.5)).contains("longer"));
        assertTrue(CalibrationService.interpretShelf(5, Math.log(0.5)).contains("faster"));
        assertTrue(CalibrationService.interpretShelf(5, 0.0).contains("±15 %"));
    }

    @Test
    void worstKeysResolveNamesAndBias() {
        var worst = List.of(
                new MarketModelResiduals.KeySummary(100, 66, false, 12, Math.log(1.2), 0.1),
                new MarketModelResiduals.KeySummary(999, 9999, true, 7, Math.log(0.9), 0.2));
        var keys = service(List.of(), Map.of(), worst).worstKeys(14, 10);

        assertEquals(2, keys.size());
        assertEquals("Iron Ore", keys.get(0).itemName());
        assertEquals("Odin", keys.get(0).worldName());
        assertEquals(0.2, keys.get(0).bias(), 1e-9);
        assertEquals("999", keys.get(1).itemName(), "unknown items fall back to the id");
        assertEquals("9999", keys.get(1).worldName(), "unknown worlds fall back to the id");
    }

    private static final class StubResiduals extends MarketModelResiduals {
        private final List<KeySummary> worst;

        StubResiduals(List<KeySummary> worst) {
            super(null);
            this.worst = worst;
        }

        @Override
        public List<KeySummary> worstKeys(int windowDays, int minCount, int limit) {
            return worst;
        }
    }

    private static final class StubEpisodes extends ListingEpisodes {
        private final List<SoldEpisode> sold;

        StubEpisodes(List<SoldEpisode> sold) {
            super(null);
            this.sold = sold;
        }

        @Override
        public List<SoldEpisode> recentSold(int windowDays, int limit) {
            return sold;
        }
    }

    private static final class StubModels extends MarketModels {
        private final Map<Integer, MarketModel> models;

        StubModels(Map<Integer, MarketModel> models) {
            super(null);
            this.models = models;
        }

        @Override
        public Map<Integer, MarketModel> findAll(int worldId, Collection<Integer> itemIds, boolean hq) {
            var out = new HashMap<Integer, MarketModel>();
            for (int id : itemIds) {
                var m = models.get(id);
                if (m != null) out.put(id, m);
            }
            return out;
        }
    }
}
