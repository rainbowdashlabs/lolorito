/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.repository.AlertRules;
import de.chojo.lolorito.repository.ItemDetail;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Periodic alert scanner. Every {@code value.alertScanIntervalMinutes}
 * minutes it walks the enabled rules, probes each against the current
 * cheapest listing, and dispatches through {@link AlertDispatcher} for the
 * rules that {@link AlertMatcher#shouldFire} clears.
 *
 * <p>{@code last_triggered_at} is bumped only after a successful dispatch
 * call — a Discord outage means the alert re-tries next scan, subject to
 * the cooldown.
 */
@Singleton
public class AlertScanner implements Runnable {

    private static final Logger log = getLogger(AlertScanner.class);

    private final AlertRules rules;
    private final ItemDetail listings;
    private final AlertDispatcher dispatcher;
    private final int freshnessHours;

    @Inject
    public AlertScanner(
            Threading threading, File config, AlertRules rules, ItemDetail listings, AlertDispatcher dispatcher) {
        this.rules = rules;
        this.listings = listings;
        this.dispatcher = dispatcher;
        this.freshnessHours = config.value().listingFreshnessHours();
        long interval = Math.max(1, config.value().alertScanIntervalMinutes());
        threading.botWorker().scheduleWithFixedDelay(this, interval, interval, TimeUnit.MINUTES);
        log.info("AlertScanner scheduled every {} minutes", interval);
    }

    @Override
    public void run() {
        try {
            var open = rules.listEnabled();
            if (open.isEmpty()) return;
            Instant now = Instant.now();
            var prices = priceLookup(open);
            int fired = 0;
            for (AlertRule rule : open) {
                Integer price = prices.get(cacheKey(rule));
                if (!AlertMatcher.shouldFire(rule, price, now)) continue;
                try {
                    dispatcher.dispatch(rule, price);
                    rules.updateLastTriggered(rule.id(), now);
                    fired++;
                } catch (Exception e) {
                    log.warn("Alert dispatch failed for rule {}", rule.id(), e);
                }
            }
            if (fired > 0) log.info("AlertScanner fired {} of {} rule(s)", fired, open.size());
        } catch (Exception e) {
            log.warn("AlertScanner run failed", e);
        }
    }

    /**
     * Group enabled rules by scope + hq, then run one batched
     * cheapest-price query per group instead of one query per rule. Cuts
     * the per-cycle DB round-trip count from O(N) to O(distinct scope × hq).
     */
    private Map<String, Integer> priceLookup(List<AlertRule> open) {
        Map<String, List<Integer>> byGroup = new HashMap<>();
        for (AlertRule rule : open) {
            byGroup.computeIfAbsent(groupKey(rule), k -> new ArrayList<>()).add(rule.itemId());
        }
        Map<String, Integer> out = new HashMap<>();
        for (var entry : byGroup.entrySet()) {
            // Any rule in the group carries the same scope/hq, so peel one to derive the filters.
            AlertRule sample = open.stream()
                    .filter(r -> groupKey(r).equals(entry.getKey()))
                    .findFirst()
                    .orElseThrow();
            var scoped = listings.cheapestByKeys(
                    entry.getValue(),
                    sample.scope().worldId(),
                    sample.scope().dataCenterId(),
                    sample.hq(),
                    freshnessHours);
            for (var priced : scoped.entrySet()) {
                out.put(
                        cacheKey(sample.scope().worldId(), sample.scope().dataCenterId(), sample.hq(), priced.getKey()),
                        priced.getValue());
            }
        }
        return out;
    }

    private static String groupKey(AlertRule rule) {
        return rule.scope().worldId() + "|" + rule.scope().dataCenterId() + "|" + rule.hq();
    }

    private static String cacheKey(AlertRule rule) {
        return cacheKey(rule.scope().worldId(), rule.scope().dataCenterId(), rule.hq(), rule.itemId());
    }

    private static String cacheKey(Integer worldId, Integer dcId, Boolean hq, int itemId) {
        return worldId + "|" + dcId + "|" + hq + "|" + itemId;
    }
}
