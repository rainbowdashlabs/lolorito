/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.function.Function;

/**
 * Thin wrapper over Caffeine that a service uses for its hot-path response
 * cache. When {@code ttlSeconds <= 0} the cache is disabled and every
 * {@link #get(Object, Function)} falls through to the loader — that's the
 * "cache off" mode both prod (configurable) and tests (fixed at zero) use.
 */
public final class ResponseCache<K, V> {

    private final Cache<K, V> delegate;

    public ResponseCache(int ttlSeconds, int maxSize) {
        if (ttlSeconds > 0) {
            this.delegate = Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                    .maximumSize(Math.max(1, maxSize))
                    .build();
        } else {
            this.delegate = null;
        }
    }

    /**
     * Look up {@code key}; call {@code loader} on a miss and memoise the result.
     */
    public V get(K key, Function<K, V> loader) {
        if (delegate == null) return loader.apply(key);
        return delegate.get(key, loader);
    }

    /**
     * Best-effort current entry count. Exposed for tests and future metrics.
     */
    public long estimatedSize() {
        return delegate == null ? 0 : delegate.estimatedSize();
    }

    /**
     * Drop every cached entry. Callers can wire this to admin endpoints later.
     */
    public void invalidateAll() {
        if (delegate != null) delegate.invalidateAll();
    }

    /**
     * Whether caching is on. Useful for tests that need to know the mode.
     */
    public boolean enabled() {
        return delegate != null;
    }
}
