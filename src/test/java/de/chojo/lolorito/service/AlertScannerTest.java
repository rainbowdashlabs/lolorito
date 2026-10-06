/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.entity.AlertKind;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.entity.AlertScope;
import de.chojo.lolorito.repository.AlertRules;
import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.repository.SalesTrends;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlertScannerTest {

    private static AlertRule rule(long userId, int itemId, int threshold, AlertKind kind, Instant last) {
        return new AlertRule(
                UUID.randomUUID(),
                userId,
                itemId,
                AlertScope.forWorld(66),
                null,
                kind,
                threshold,
                true,
                60,
                last,
                Instant.EPOCH);
    }

    @Test
    void dispatchesAndRecordsWhenThresholdCrossed() {
        var rules = new StubRules();
        var listings = new StubListings();
        var sales = new StubSales();
        var dispatcher = new StubDispatcher();

        var r = rule(1L, 100, 500, AlertKind.PRICE_BELOW, null);
        rules.inserted.put(r.id(), r);
        listings.prices.put(100, 400);

        var scanner = new AlertScanner(new Threading(), new File(), rules, listings, sales, dispatcher);
        scanner.run();

        assertThat(dispatcher.fired).hasSize(1);
        assertThat(dispatcher.fired.getFirst().observed).isEqualTo(400);
        assertThat(rules.triggered).containsKey(r.id());
    }

    @Test
    void skipsWhenThresholdNotCrossed() {
        var rules = new StubRules();
        var listings = new StubListings();
        var sales = new StubSales();
        var dispatcher = new StubDispatcher();

        var r = rule(1L, 100, 500, AlertKind.PRICE_BELOW, null);
        rules.inserted.put(r.id(), r);
        listings.prices.put(100, 800);

        var scanner = new AlertScanner(new Threading(), new File(), rules, listings, sales, dispatcher);
        scanner.run();

        assertThat(dispatcher.fired).isEmpty();
        assertThat(rules.triggered).isEmpty();
    }

    @Test
    void dispatcherExceptionDoesNotAdvanceCooldown() {
        var rules = new StubRules();
        var listings = new StubListings();
        var sales = new StubSales();
        var dispatcher = new StubDispatcher();
        dispatcher.throwOnDispatch = true;

        var r = rule(1L, 100, 500, AlertKind.PRICE_BELOW, null);
        rules.inserted.put(r.id(), r);
        listings.prices.put(100, 400);

        var scanner = new AlertScanner(new Threading(), new File(), rules, listings, sales, dispatcher);
        scanner.run();

        assertThat(rules.triggered).isEmpty();
    }

    @Test
    void emptyEnabledListIsANoop() {
        var scanner = new AlertScanner(
                new Threading(),
                new File(),
                new StubRules(),
                new StubListings(),
                new StubSales(),
                new StubDispatcher());
        scanner.run(); // must not throw
    }

    @Test
    void listingCountDropFiresAtZeroListings() {
        var rules = new StubRules();
        var listings = new StubListings();
        var sales = new StubSales();
        var dispatcher = new StubDispatcher();

        var r = rule(1L, 100, 2, AlertKind.LISTING_COUNT_DROP, null);
        rules.inserted.put(r.id(), r);
        listings.counts.put(100, 0);

        new AlertScanner(new Threading(), new File(), rules, listings, sales, dispatcher).run();

        assertThat(dispatcher.fired).hasSize(1);
        assertThat(dispatcher.fired.getFirst().observed).isZero();
    }

    @Test
    void listingCountDropSkipsItemsWithoutFreshData() {
        var rules = new StubRules();
        var dispatcher = new StubDispatcher();
        var r = rule(1L, 100, 2, AlertKind.LISTING_COUNT_DROP, null);
        rules.inserted.put(r.id(), r);

        new AlertScanner(new Threading(), new File(), rules, new StubListings(), new StubSales(), dispatcher).run();

        assertThat(dispatcher.fired).isEmpty();
    }

    @Test
    void saleVolumeSpikeFiresAgainstTheTrailingAverage() {
        var rules = new StubRules();
        var listings = new StubListings();
        var sales = new StubSales();
        var dispatcher = new StubDispatcher();

        var spiking = rule(1L, 100, 200, AlertKind.SALE_VOLUME_SPIKE, null);
        var steady = rule(1L, 101, 200, AlertKind.SALE_VOLUME_SPIKE, null);
        rules.inserted.put(spiking.id(), spiking);
        rules.inserted.put(steady.id(), steady);
        sales.windows.put(100, new SalesTrends.VolumeWindow(30, 70));
        sales.windows.put(101, new SalesTrends.VolumeWindow(12, 70));

        new AlertScanner(new Threading(), new File(), rules, listings, sales, dispatcher).run();

        assertThat(dispatcher.fired).hasSize(1);
        assertThat(dispatcher.fired.getFirst().rule().itemId()).isEqualTo(100);
        assertThat(dispatcher.fired.getFirst().observed).isEqualTo(300);
    }

    @Test
    void priceAndCountRulesOnTheSameItemAreMeasuredSeparately() {
        var rules = new StubRules();
        var listings = new StubListings();
        var dispatcher = new StubDispatcher();

        var price = rule(1L, 100, 500, AlertKind.PRICE_BELOW, null);
        var count = rule(1L, 100, 1, AlertKind.LISTING_COUNT_DROP, null);
        rules.inserted.put(price.id(), price);
        rules.inserted.put(count.id(), count);
        listings.prices.put(100, 400);
        listings.counts.put(100, 7);

        new AlertScanner(new Threading(), new File(), rules, listings, new StubSales(), dispatcher).run();

        assertThat(dispatcher.fired).extracting(f -> f.rule().kind()).containsExactly(AlertKind.PRICE_BELOW);
    }

    // -- Stubs -------------------------------------------------------------

    private static final class StubSales extends SalesTrends {
        final Map<Integer, VolumeWindow> windows = new HashMap<>();

        StubSales() {
            super(null);
        }

        @Override
        public Map<Integer, VolumeWindow> volumeByKeys(
                List<Integer> itemIds, Integer worldId, Integer dcId, Boolean hq, int baselineDays) {
            Map<Integer, VolumeWindow> out = new HashMap<>();
            for (int id : itemIds) {
                var w = windows.get(id);
                if (w != null) out.put(id, w);
            }
            return out;
        }
    }

    private static final class StubRules extends AlertRules {
        final Map<UUID, AlertRule> inserted = new HashMap<>();
        final Map<UUID, Instant> triggered = new HashMap<>();

        @Override
        public List<AlertRule> listEnabled() {
            return inserted.values().stream().filter(AlertRule::enabled).toList();
        }

        @Override
        public void updateLastTriggered(UUID id, Instant when) {
            triggered.put(id, when);
        }
    }

    private static final class StubListings extends ItemDetail {
        final Map<Integer, Integer> prices = new HashMap<>();
        final Map<Integer, Integer> counts = new HashMap<>();

        @Override
        public Map<Integer, Integer> listingCountByKeys(
                List<Integer> itemIds, Integer worldId, Integer dcId, Boolean hq, int maxAgeHours) {
            Map<Integer, Integer> out = new HashMap<>();
            for (int id : itemIds) {
                Integer c = counts.get(id);
                if (c != null) out.put(id, c);
            }
            return out;
        }

        @Override
        public Optional<Integer> cheapestPrice(int itemId, Integer worldId, Integer dcId, Boolean hq) {
            return Optional.ofNullable(prices.get(itemId));
        }

        @Override
        public Map<Integer, Integer> cheapestByKeys(
                List<Integer> itemIds, Integer worldId, Integer dcId, Boolean hq, int maxAgeHours) {
            Map<Integer, Integer> out = new HashMap<>();
            for (int id : itemIds) {
                Integer p = prices.get(id);
                if (p != null) out.put(id, p);
            }
            return out;
        }
    }

    private static final class StubDispatcher implements AlertDispatcher {
        final List<Fired> fired = new ArrayList<>();
        boolean throwOnDispatch = false;

        @Override
        public void dispatch(AlertRule rule, int observed) {
            if (throwOnDispatch) throw new IllegalStateException("boom");
            fired.add(new Fired(rule, observed));
        }

        record Fired(AlertRule rule, int observed) {}
    }
}
