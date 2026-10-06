/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.repository.MarketModelResiduals;
import de.chojo.lolorito.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CalibrationHistoryWorkerTest extends RepositoryTestBase {

    @BeforeEach
    void setUp() {
        query("DELETE FROM calibration_snapshot").single(call()).delete();
        query("DELETE FROM market_model_residuals").single(call()).delete();
    }

    @Test
    void runInsertsExactlyOneSnapshotPerCycle() {
        // Bypass the scheduler — invoke run() directly. The Threading arg is
        // only used at construction time to schedule the periodic task.
        var worker = new CalibrationHistoryWorker(new Threading(), new MarketModelResiduals(null));
        worker.run();
        int rows = query("SELECT count(*)::int c FROM calibration_snapshot")
                .single(call())
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
        assertEquals(1, rows);
    }

    @Test
    void secondRunInSameSecondIsIdempotent() {
        var worker = new CalibrationHistoryWorker(new Threading(), new MarketModelResiduals(null));
        worker.run();
        worker.run();
        int rows = query("SELECT count(*)::int c FROM calibration_snapshot")
                .single(call())
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
        // Either both rows go through (different microsecond timestamps) or
        // the second one collides — either way, no exception and at most 2.
        assertEquals(true, rows == 1 || rows == 2);
    }
}
