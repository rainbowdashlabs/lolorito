/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.repository.MarketModelResiduals;
import de.chojo.lolorito.repository.MarketModels;
import de.chojo.lolorito.value.MarketModel;
import de.chojo.lolorito.value.PriceDistribution;
import de.chojo.lolorito.value.SaleRate;
import de.chojo.universalis.entities.Price;
import de.chojo.universalis.entities.Sale;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidualRecorderTest {

    private static final World WORLD = Worlds.worldById(66);
    private static final int ITEM_ID = 100;

    private static MarketModel modelAt(double medianPrice) {
        var mu = Math.log(medianPrice);
        return new MarketModel(
                ITEM_ID,
                WORLD.id(),
                false,
                new PriceDistribution(mu, 0.0, 100),
                new SaleRate(1.5, 1.0, 0.6),
                0.0,
                0.0,
                100,
                true,
                false,
                Instant.now());
    }

    private static Sale sale(int unit, Instant when, boolean hq) {
        return new Sale(hq, new Price(unit, 1, unit), when, false, WORLD, null);
    }

    @Test
    void writesLogRatioAgainstPredictedPrice() {
        var models = new StubModels(Optional.of(modelAt(1000.0)));
        var recorded = new RecordingResiduals();
        var recorder = new ResidualRecorder(models, recorded);
        recorder.record(WORLD, ITEM_ID, List.of(sale(2000, Instant.now(), false)));
        assertEquals(1, recorded.inserted.size());
        var row = recorded.inserted.getFirst();
        assertEquals(Math.log(2.0), row.logRatio(), 1e-9, "log(2000/1000) = ln 2");
    }

    @Test
    void skipsSaleWhenNoModel() {
        var recorded = new RecordingResiduals();
        var recorder = new ResidualRecorder(new StubModels(Optional.empty()), recorded);
        recorder.record(WORLD, ITEM_ID, List.of(sale(1500, Instant.now(), false)));
        assertTrue(recorded.inserted.isEmpty());
    }

    @Test
    void skipsRowsWithNonPositivePrice() {
        var recorded = new RecordingResiduals();
        var recorder = new ResidualRecorder(new StubModels(Optional.of(modelAt(1000.0))), recorded);
        recorder.record(WORLD, ITEM_ID, List.of(sale(0, Instant.now(), false)));
        assertTrue(recorded.inserted.isEmpty());
    }

    @Test
    void recordsHqAndNqIndependently() {
        var recorded = new RecordingResiduals();
        var recorder = new ResidualRecorder(new StubModels(Optional.of(modelAt(1000.0))), recorded);
        recorder.record(WORLD, ITEM_ID, List.of(sale(1000, Instant.now(), false), sale(1500, Instant.now(), true)));
        assertEquals(2, recorded.inserted.size());
        assertEquals(false, recorded.inserted.get(0).hq());
        assertEquals(true, recorded.inserted.get(1).hq());
    }

    // --- Stubs -----------------------------------------------------------

    private static final class StubModels extends MarketModels {
        private final Optional<MarketModel> model;

        StubModels(Optional<MarketModel> model) {
            super(null);
            this.model = model;
        }

        @Override
        public Optional<MarketModel> find(int worldId, int itemId, boolean hq) {
            return model;
        }
    }

    private static final class RecordingResiduals extends MarketModelResiduals {
        final AtomicInteger calls = new AtomicInteger();
        final List<Row> inserted = new ArrayList<>();

        RecordingResiduals() {
            super(null);
        }

        @Override
        public void insert(int itemId, int worldId, boolean hq, Instant observedAt, double logRatio) {
            calls.incrementAndGet();
            inserted.add(new Row(itemId, worldId, hq, logRatio));
        }
    }

    private record Row(int itemId, int worldId, boolean hq, double logRatio) {}
}
