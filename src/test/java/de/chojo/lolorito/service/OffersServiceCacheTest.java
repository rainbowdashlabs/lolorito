/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.repository.Offers;
import de.chojo.lolorito.value.UserPrefs;
import de.chojo.universalis.provider.NameSupplier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OffersServiceCacheTest {

    @Test
    void repeatedCallsWithSameKeyHitCacheOnce() {
        var repo = new CountingOffers();
        var service = new OffersService(new File(), repo, NameSupplier.EMPTY);
        var prefs = UserPrefs.defaults();
        service.topOffers(66, 7, 6, prefs, 10);
        service.topOffers(66, 7, 6, prefs, 10);
        service.topOffers(66, 7, 6, prefs, 10);
        assertEquals(1, repo.calls.get(), "second and third call should be served from the cache");
    }

    @Test
    void differentKeysMissTheCache() {
        var repo = new CountingOffers();
        var service = new OffersService(new File(), repo, NameSupplier.EMPTY);
        var prefs = UserPrefs.defaults();
        service.topOffers(66, 7, 6, prefs, 10);
        service.topOffers(66, 7, 6, prefs, 20); // different limit
        service.topOffers(66, 7, 12, prefs, 10); // different refreshHours
        assertEquals(3, repo.calls.get());
    }

    @Test
    void invalidateCacheForcesReload() {
        var repo = new CountingOffers();
        var service = new OffersService(new File(), repo, NameSupplier.EMPTY);
        var prefs = UserPrefs.defaults();
        service.topOffers(66, 7, 6, prefs, 10);
        service.invalidateCache();
        service.topOffers(66, 7, 6, prefs, 10);
        assertEquals(2, repo.calls.get());
    }

    @Test
    void uncachedVariantBypassesCache() {
        var repo = new CountingOffers();
        var service = new OffersService(new File(), repo, NameSupplier.EMPTY);
        var prefs = UserPrefs.defaults();
        service.topOffersUncached(66, 7, 6, prefs, 10);
        service.topOffersUncached(66, 7, 6, prefs, 10);
        assertEquals(2, repo.calls.get());
    }

    /**
     * Fake repo that counts every SQL call so we can prove the cache short-circuits.
     */
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
