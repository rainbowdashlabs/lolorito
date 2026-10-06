/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.config.file.File;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlannerServiceTest {

    private static PlannerService.PlanRequest req(
            Long budget,
            Integer slots,
            Double attentionHours,
            Double attentionFraction,
            Integer hopWeight,
            Integer maxWorlds,
            Integer topK) {
        return new PlannerService.PlanRequest(
                null,
                null,
                budget,
                slots,
                attentionHours,
                attentionFraction,
                hopWeight,
                maxWorlds,
                topK,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void paramsForFallsBackToConfigDefaultsWhenBodyIsEmpty() {
        var config = new File();
        var p = PlannerService.paramsFor(config, 1, 2, req(null, null, null, null, null, null, null));
        var cp = config.planner();
        assertEquals(cp.budget(), p.budget());
        assertEquals(cp.inventorySlots(), p.inventorySlots());
        assertEquals(cp.attentionBudgetHours(), p.attentionBudgetHours(), 1e-9);
        assertEquals(cp.attentionFraction(), p.attentionFraction(), 1e-9);
        assertEquals(cp.hopWeightGilPerSecond(), p.hopWeightGilPerSecond());
        assertEquals(cp.maxWorlds(), p.maxWorlds());
        assertEquals(cp.candidateTopK(), p.candidateTopK());
    }

    @Test
    void paramsForClampsBelowMinima() {
        var config = new File();
        var p = PlannerService.paramsFor(config, 1, 2, req(0L, 0, 0.0, 0.0, 0, 0, 5));
        // Every field lands on its lower bound.
        assertEquals(1L, p.budget());
        assertEquals(1, p.inventorySlots());
        assertEquals(0.05, p.attentionBudgetHours(), 1e-9);
        assertEquals(0.01, p.attentionFraction(), 1e-9);
        assertEquals(1, p.hopWeightGilPerSecond());
        assertEquals(1, p.maxWorlds());
        assertEquals(10, p.candidateTopK());
    }

    @Test
    void paramsForClampsAboveMaxima() {
        var config = new File();
        var p = PlannerService.paramsFor(
                config, 1, 2, req(10_000_000_000L, 999_999, 999.0, 5.0, 999_999_999, 99, 9999));
        assertEquals(1_000_000_000L, p.budget());
        assertEquals(5000, p.inventorySlots());
        assertEquals(24.0, p.attentionBudgetHours(), 1e-9);
        assertEquals(1.0, p.attentionFraction(), 1e-9);
        assertEquals(100_000, p.hopWeightGilPerSecond());
        assertEquals(8, p.maxWorlds());
        assertEquals(2000, p.candidateTopK());
    }

    @Test
    void paramsForPropagatesHomeWorldAndDcVerbatim() {
        var p = PlannerService.paramsFor(new File(), 77, 99, req(null, null, null, null, null, null, null));
        assertEquals(77, p.homeWorldId());
        assertEquals(99, p.homeDataCenterId());
    }
}
