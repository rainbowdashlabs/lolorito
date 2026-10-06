/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.auth;

import com.google.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory token bucket used by {@link AuthRoutes} to blunt
 * brute-force login churn from a single IP. Not distributed — a
 * dedicated-instance deployment behind a reverse proxy is expected.
 *
 * <p>Default budget is {@link #MAX_TOKENS} attempts per key over the
 * refill window. First hit refills to full; subsequent hits deduct one
 * token; refill happens linearly.
 */
@Singleton
public class AuthRateLimiter {

    /** Maximum simultaneous attempts allowed per IP before we shed. */
    static final int MAX_TOKENS = 20;

    /** Milliseconds it takes to refill one token. 20 tokens per 30s = ~40 attempts / min. */
    static final long REFILL_MS_PER_TOKEN = 1_500;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** @return {@code true} when the caller is allowed to proceed; {@code false} when rate-limited. */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(MAX_TOKENS, now));
        synchronized (b) {
            long elapsed = now - b.lastRefillMs;
            if (elapsed > 0) {
                long refill = elapsed / REFILL_MS_PER_TOKEN;
                if (refill > 0) {
                    b.tokens = Math.min(MAX_TOKENS, b.tokens + refill);
                    b.lastRefillMs += refill * REFILL_MS_PER_TOKEN;
                }
            }
            if (b.tokens <= 0) return false;
            b.tokens -= 1;
            return true;
        }
    }

    /** Package-visible for tests. */
    void clear() {
        buckets.clear();
    }

    private static final class Bucket {
        long tokens;
        long lastRefillMs;

        Bucket(long tokens, long now) {
            this.tokens = tokens;
            this.lastRefillMs = now;
        }
    }
}
