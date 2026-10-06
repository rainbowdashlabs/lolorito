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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlannerServiceCacheTest {

    private static PlannerParams params() {
        // Odin (66) on Light (7). Any valid Worlds entry works — the loader returns [] so no plan happens.
        return new PlannerParams(66, 7, 1_000_000L, 100, 8.0, 0.25, 500, 5, 250, 15, 45, 200_000, 0, 40, 20, 0.05, 6.0);
    }

    @Test
    void repeatedPlanCallsMemoise() {
        var repo = new CountingOffers();
        var service =
                new PlannerService(new File(), repo, NameSupplier.EMPTY, new ItemCatalog(), null, null, null, null);
        service.plan(params(), 6);
        service.plan(params(), 6);
        service.plan(params(), 6);
        assertEquals(1, repo.calls.get());
    }

    @Test
    void differentRefreshHoursBypassCache() {
        var repo = new CountingOffers();
        var service =
                new PlannerService(new File(), repo, NameSupplier.EMPTY, new ItemCatalog(), null, null, null, null);
        service.plan(params(), 6);
        service.plan(params(), 12);
        assertEquals(2, repo.calls.get());
    }

    @Test
    void planUncachedBypassesCache() {
        var repo = new CountingOffers();
        var service =
                new PlannerService(new File(), repo, NameSupplier.EMPTY, new ItemCatalog(), null, null, null, null);
        service.planUncached(params(), 6);
        service.planUncached(params(), 6);
        assertEquals(2, repo.calls.get());
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
