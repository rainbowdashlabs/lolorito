/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.planner.PlannerParams;
import de.chojo.lolorito.repository.Offers;
import de.chojo.universalis.provider.NameSupplier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerServiceReplanTest {

    private static PlannerParams params() {
        return new PlannerParams(66, 7, 1_000_000L, 100, 8.0, 0.25, 500, 5, 250, 15, 45, 200_000, 0, 40, 20, 0.05, 6.0);
    }

    @Test
    void replanBypassesCache() {
        var repo = new CountingOffers();
        var service =
                new PlannerService(new File(), repo, NameSupplier.EMPTY, new ItemCatalog(), null, null, null, null);
        service.plan(params(), 6); // populates cache
        service.plan(params(), 6); // cache hit
        assertEquals(1, repo.calls.get());
        service.replan(params(), 6, new PlannerService.ReplanContext(Set.of(), 0L, 0));
        assertEquals(2, repo.calls.get(), "replan must always fetch fresh candidates");
    }

    @Test
    void replanClampsBudgetAndInventoryToOneMinimum() {
        var repo = new CountingOffers();
        var service =
                new PlannerService(new File(), repo, NameSupplier.EMPTY, new ItemCatalog(), null, null, null, null);
        // Spend all the budget and inventory; the service must not blow up on remaining=0.
        var plan = service.replan(
                params(), 6, new PlannerService.ReplanContext(Set.of(), Long.MAX_VALUE, Integer.MAX_VALUE));
        assertTrue(plan.stops().isEmpty());
    }

    @Test
    void replanFailsOnUnknownHomeWorld() {
        var repo = new CountingOffers();
        var service =
                new PlannerService(new File(), repo, NameSupplier.EMPTY, new ItemCatalog(), null, null, null, null);
        var bogus = new PlannerParams(
                Integer.MAX_VALUE, 7, 1_000L, 10, 1.0, 0.25, 500, 5, 250, 15, 45, 200_000, 0, 40, 20, 0.05, 6.0);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.replan(bogus, 6, new PlannerService.ReplanContext(Set.of(), 0L, 0)));
    }

    @Test
    void paramsForReplanClampsSameAsPlanRequest() {
        var config = new File();
        var req = new PlannerService.ReplanRequest(
                null, null, 0L, 0, 0.0, 0.0, 0, 0, 5, null, null, null, null, null, Set.of(66), 0L, 0, null, null);
        var params = PlannerService.paramsFor(config, 66, 7, req);
        assertEquals(1L, params.budget(), "shared clamping — 0 → 1");
        assertEquals(1, params.inventorySlots());
        assertEquals(0.05, params.attentionBudgetHours(), 1e-9);
    }

    private static final class CountingOffers extends Offers {
        final AtomicInteger calls = new AtomicInteger();

        CountingOffers() {
            super(null);
        }

        @Override
        public List<Candidate> candidates(
                int homeWorldId,
                int dataCenterId,
                String regionName,
                de.chojo.lolorito.entity.OfferFilterTarget scope,
                int refreshHours,
                int cap) {
            calls.incrementAndGet();
            return List.of();
        }
    }
}
