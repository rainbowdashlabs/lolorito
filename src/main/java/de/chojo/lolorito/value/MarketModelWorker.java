/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.repository.MarketModels;
import de.chojo.lolorito.repository.PerfMetrics;
import de.chojo.lolorito.web.ValuationBroadcaster;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Periodically refits per-(item, world, hq) market models against the
 * recent-sales stream.
 *
 * <p>Each cycle asks {@link MarketModels#refitCandidates} for the keys that
 * (a) have any sales in the fit window and (b) either have no model row or
 * one that's stale relative to the configured refresh interval. Those keys
 * fan out onto a virtual-thread pool that does the pure math in parallel.
 *
 * <p>Wired as an eager singleton — the constructor itself registers the
 * periodic task with {@link Threading#botWorker()}.
 */
@Singleton
public class MarketModelWorker implements Runnable {
    private static final Logger log = getLogger(MarketModelWorker.class);

    private final File config;
    private final MarketModels dao;
    private final MarketModelFitter fitter;
    private final ExecutorService fanOut;
    /** Deferred so we don't wire a cycle at boot — the broadcaster lives in web.*. */
    private final Provider<ValuationBroadcaster> broadcaster;

    private final PerfMetrics perfMetrics;

    @Inject
    public MarketModelWorker(
            Threading threading,
            File config,
            MarketModels dao,
            MarketModelFitter fitter,
            Provider<ValuationBroadcaster> broadcaster,
            PerfMetrics perfMetrics) {
        this.config = config;
        this.dao = dao;
        this.fitter = fitter;
        this.broadcaster = broadcaster;
        this.perfMetrics = perfMetrics;
        this.fanOut = Executors.newVirtualThreadPerTaskExecutor();
        long interval = Math.max(1, config.value().modelRefreshMinutes());
        // Small initial delay so the first cycle doesn't race with startup.
        threading.botWorker().scheduleWithFixedDelay(this, 30, interval * 60L, TimeUnit.SECONDS);
        log.info("MarketModelWorker scheduled every {} minutes", interval);
    }

    @Override
    public void run() {
        try {
            Instant now = Instant.now();
            int expired = dao.expireStale(config.value().rateWindowDays());
            if (expired > 0) {
                log.info("Market model expiry: {} stale models demoted to insufficient", expired);
            }
            var cutoff = now.minus(Duration.ofMinutes(config.value().modelRefreshMinutes()));
            List<MarketModels.KeyRef> keys = dao.refitCandidates(
                    cutoff, config.value().rateWindowDays(), config.value().refitBatchSize());
            if (keys.isEmpty()) {
                log.debug("Market model refit: no candidates due");
                return;
            }
            long start = System.currentTimeMillis();
            var broadcasterInstance = safeBroadcaster();
            var futures = keys.stream()
                    .map(k -> fanOut.submit(() -> {
                        var model = fitter.fit(k.worldId(), k.itemId(), k.hq(), now);
                        if (model.isEmpty()) return new FitOutcome(0, 0);
                        var m = model.get();
                        dao.upsert(m);
                        if (broadcasterInstance != null) {
                            broadcasterInstance.broadcastRefit(m.itemId(), m.worldId(), m.hq());
                        }
                        return new FitOutcome(1, m.sufficient() ? 1 : 0);
                    }))
                    .toList();
            int fitted = 0;
            int sufficient = 0;
            for (var f : futures) {
                var outcome = f.get();
                fitted += outcome.fitted();
                sufficient += outcome.sufficient();
            }
            long ms = System.currentTimeMillis() - start;
            log.info(
                    "Market model refit: {} keys, {} fitted ({} sufficient) in {} ms",
                    keys.size(),
                    fitted,
                    sufficient,
                    ms);
            try {
                perfMetrics.record(
                        "model_refit_ms",
                        ms,
                        keys.size() + " keys / " + fitted + " fitted / " + sufficient + " sufficient");
            } catch (Exception e) {
                log.debug("perf metric record failed", e);
            }
        } catch (Exception e) {
            log.warn("Market model refit failed", e);
        }
    }

    private ValuationBroadcaster safeBroadcaster() {
        try {
            return broadcaster.get();
        } catch (Exception e) {
            return null;
        }
    }

    /** Per-key fit outcome so the summary can distinguish "written" from "trustworthy". */
    private record FitOutcome(int fitted, int sufficient) {}
}
