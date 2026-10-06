/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.repository.SalesTrends;
import de.chojo.universalis.entities.Language;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendServiceTest {

    private static final SalesTrends.Trend RISING = new SalesTrends.Trend(100, false, 3.0, 2.0, 0.8, 70, 10.0, 15);
    private static final SalesTrends.Trend STEEP = new SalesTrends.Trend(101, true, 6.0, 1.0, 0.2, 90, 12.0, 30);
    private static final SalesTrends.Trend DYING = new SalesTrends.Trend(102, false, -4.0, 10.0, 0.9, 40, 8.0, 1);
    private static final SalesTrends.Trend FLAT = new SalesTrends.Trend(103, false, 0.0, 5.0, 0.0, 35, 5.0, 5);

    private static TrendService service(StubTrends repo) {
        var names = TestNames.of(
                Map.of(100, TestNames.english("Iron Ore", "Eisenerz"), 101, TestNames.english("Gold Ore", "")));
        return new TrendService(new File(), repo, names);
    }

    @Test
    void boardSplitsAndRanksBySlope() {
        var repo = new StubTrends(List.of(RISING, STEEP, DYING, FLAT));
        var board = service(repo).board(66, 7, 10, Language.ENGLISH);

        assertEquals(4, board.fittedKeys());
        assertEquals(
                List.of(101, 100),
                board.trending().stream().map(TrendService.TrendRow::itemId).toList());
        assertEquals(
                List.of(102),
                board.losing().stream().map(TrendService.TrendRow::itemId).toList());
    }

    @Test
    void boardHonoursTheLimit() {
        var board = service(new StubTrends(List.of(RISING, STEEP))).board(66, 7, 1, Language.ENGLISH);
        assertEquals(1, board.trending().size());
        assertEquals(101, board.trending().getFirst().itemId());
    }

    @Test
    void boardIsCachedPerRequestShape() {
        var repo = new StubTrends(List.of(RISING));
        var service = service(repo);
        var first = service.board(66, 7, 10, Language.ENGLISH);
        assertSame(first, service.board(66, 7, 10, Language.ENGLISH));
        assertEquals(1, repo.fitCalls);
    }

    @Test
    void rowsForecastClampAndFlagWeakFits() {
        var board = service(new StubTrends(List.of(RISING, STEEP, DYING))).board(66, 7, 10, Language.ENGLISH);
        var rising = board.trending().get(1);
        assertEquals(23, rising.predictedNext24h(), "intercept 2 + slope 3 × 7");
        assertEquals(0.3, rising.relativeSlope(), 1e-9);
        assertFalse(rising.weakFit());
        assertTrue(board.trending().getFirst().weakFit(), "r² 0.2 is below the weak-fit bar");
        assertEquals(0, board.losing().getFirst().predictedNext24h(), "a falling line clamps at zero");
    }

    @Test
    void namesUseTheRequestedLanguageWithEnglishAndIdFallbacks() {
        var board = service(new StubTrends(List.of(RISING, STEEP, DYING))).board(66, 7, 10, Language.GERMAN);
        assertEquals("Eisenerz", board.trending().get(1).itemName());
        assertEquals("Gold Ore", board.trending().getFirst().itemName(), "blank German name falls back to English");
        assertEquals("102", board.losing().getFirst().itemName(), "unknown item falls back to the id");
    }

    @Test
    void forItemMapsTheSingleFit() {
        var row = service(new StubTrends(List.of(RISING))).forItem(66, 100, false, 7, Language.ENGLISH);
        assertTrue(row.isPresent());
        assertEquals("Iron Ore", row.get().itemName());
        assertTrue(service(new StubTrends(List.of()))
                .forItem(66, 100, false, 7, Language.ENGLISH)
                .isEmpty());
    }

    @Test
    void last24hTotalsTheHourlyBuckets() {
        var repo = new StubTrends(List.of());
        repo.hours = List.of(
                new SalesTrends.HourBucket(Instant.EPOCH, 3, 300),
                new SalesTrends.HourBucket(Instant.EPOCH.plusSeconds(3600), 2, 500));
        var activity = service(repo).last24h(66);
        assertEquals(5, activity.totalUnits());
        assertEquals(800, activity.totalGil());
        assertEquals(2, activity.hours().size());
    }

    private static final class StubTrends extends SalesTrends {
        private final List<Trend> trends;
        private List<HourBucket> hours = List.of();
        private int fitCalls;

        StubTrends(List<Trend> trends) {
            super(null);
            this.trends = trends;
        }

        @Override
        public List<Trend> fit(int worldId, int windowDays, int minUnits) {
            fitCalls++;
            return trends;
        }

        @Override
        public Optional<Trend> fitOne(int worldId, int itemId, boolean hq, int windowDays) {
            return trends.stream().filter(t -> t.itemId() == itemId).findFirst();
        }

        @Override
        public List<HourBucket> hourly(int worldId, int hours) {
            return this.hours;
        }
    }
}
