/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseCacheTest {

    @Test
    void zeroTtlDisablesCacheAndAlwaysCallsLoader() {
        var cache = new ResponseCache<String, Integer>(0, 10);
        var loads = new AtomicInteger();
        assertFalse(cache.enabled());
        cache.get("a", k -> loads.incrementAndGet());
        cache.get("a", k -> loads.incrementAndGet());
        assertEquals(2, loads.get(), "cache off → loader runs on every call");
    }

    @Test
    void positiveTtlMemoisesLoadResults() {
        var cache = new ResponseCache<String, Integer>(60, 10);
        var loads = new AtomicInteger();
        var v1 = cache.get("a", k -> loads.incrementAndGet());
        var v2 = cache.get("a", k -> loads.incrementAndGet());
        assertTrue(cache.enabled());
        assertEquals(v1, v2, "second call returns the memoised value");
        assertEquals(1, loads.get(), "loader ran only once");
    }

    @Test
    void distinctKeysGetDistinctEntries() {
        var cache = new ResponseCache<String, Integer>(60, 10);
        var loads = new AtomicInteger();
        cache.get("a", k -> loads.incrementAndGet());
        cache.get("b", k -> loads.incrementAndGet());
        assertEquals(2, cache.estimatedSize());
    }

    @Test
    void invalidateAllDropsEveryEntry() {
        var cache = new ResponseCache<String, Integer>(60, 10);
        cache.get("a", k -> 1);
        cache.get("b", k -> 2);
        cache.invalidateAll();
        // Caffeine's estimatedSize is best-effort; a load after invalidation triggers the loader again.
        var loads = new AtomicInteger();
        cache.get("a", k -> loads.incrementAndGet());
        assertEquals(1, loads.get());
    }

    @Test
    void invalidateAllIsNoOpWhenCacheIsDisabled() {
        var cache = new ResponseCache<String, Integer>(0, 10);
        cache.invalidateAll();
        assertEquals(0, cache.estimatedSize());
    }
}
