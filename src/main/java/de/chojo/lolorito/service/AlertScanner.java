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
import de.chojo.lolorito.entity.AlertKind;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.repository.AlertRules;
import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.repository.SalesTrends;
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
 * minutes it walks the enabled rules, measures the value each rule's kind
 * watches (cheapest price, sales spike percent, or listing count), and
 * dispatches through {@link AlertDispatcher} for the rules that
 * {@link AlertMatcher#shouldFire} clears.
 *
 * <p>Measurements are batched: one query per (metric, scope, hq) group
 * rather than one per rule. {@code last_triggered_at} is bumped only after
 * a successful dispatch call — a Discord outage means the alert re-tries
 * next scan, subject to the cooldown.
 */
@Singleton
public class AlertScanner implements Runnable {

    private static final Logger log = getLogger(AlertScanner.class);

    /** Days of sales the spike baseline averages over. */
    static final int SPIKE_BASELINE_DAYS = 7;

    private final AlertRules rules;
    private final ItemDetail listings;
    private final SalesTrends sales;
    private final AlertDispatcher dispatcher;
    private final int freshnessHours;

    @Inject
    public AlertScanner(
            Threading threading,
            File config,
            AlertRules rules,
            ItemDetail listings,
            SalesTrends sales,
            AlertDispatcher dispatcher) {
        this.rules = rules;
        this.listings = listings;
        this.sales = sales;
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
            var observed = observe(open);
            int fired = 0;
            for (AlertRule rule : open) {
                Integer value = observed.get(cacheKey(rule));
                if (!AlertMatcher.shouldFire(rule, value, now)) continue;
                try {
                    dispatcher.dispatch(rule, value);
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

    /** What a rule measures; price rules of either direction share one lookup. */
    private enum Metric {
        PRICE,
        VOLUME,
        COUNT;

        static Metric of(AlertKind kind) {
            return switch (kind) {
                case PRICE_BELOW, PRICE_ABOVE -> PRICE;
                case SALE_VOLUME_SPIKE -> VOLUME;
                case LISTING_COUNT_DROP -> COUNT;
            };
        }
    }

    private Map<String, Integer> observe(List<AlertRule> open) {
        Map<String, List<AlertRule>> byGroup = new HashMap<>();
        for (AlertRule rule : open) {
            byGroup.computeIfAbsent(groupKey(rule), k -> new ArrayList<>()).add(rule);
        }
        Map<String, Integer> out = new HashMap<>();
        for (var group : byGroup.values()) {
            AlertRule sample = group.getFirst();
            List<Integer> items =
                    group.stream().map(AlertRule::itemId).distinct().toList();
            for (var measured : measure(Metric.of(sample.kind()), sample, items).entrySet()) {
                out.put(cacheKey(sample, measured.getKey()), measured.getValue());
            }
        }
        return out;
    }

    private Map<Integer, Integer> measure(Metric metric, AlertRule sample, List<Integer> items) {
        Integer worldId = sample.scope().worldId();
        Integer dcId = sample.scope().dataCenterId();
        return switch (metric) {
            case PRICE -> listings.cheapestByKeys(items, worldId, dcId, sample.hq(), freshnessHours);
            case COUNT -> listings.listingCountByKeys(items, worldId, dcId, sample.hq(), freshnessHours);
            case VOLUME -> {
                Map<Integer, Integer> out = new HashMap<>();
                sales.volumeByKeys(items, worldId, dcId, sample.hq(), SPIKE_BASELINE_DAYS)
                        .forEach((item, window) -> out.put(
                                item,
                                AlertMatcher.spikePercent(
                                        window.recentUnits(), window.baselineUnits(), SPIKE_BASELINE_DAYS)));
                yield out;
            }
        };
    }

    private static String groupKey(AlertRule rule) {
        return Metric.of(rule.kind()) + "|" + rule.scope().worldId() + "|"
                + rule.scope().dataCenterId() + "|" + rule.hq();
    }

    private static String cacheKey(AlertRule rule) {
        return cacheKey(rule, rule.itemId());
    }

    private static String cacheKey(AlertRule groupMember, int itemId) {
        return groupKey(groupMember) + "|" + itemId;
    }
}
