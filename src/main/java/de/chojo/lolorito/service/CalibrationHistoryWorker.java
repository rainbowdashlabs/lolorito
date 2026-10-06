/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.repository.MarketModelResiduals;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Captures the current calibration summary once per hour so the dashboard
 * can draw a "how accurate are the market models" chart over time.
 * Rolling window matches the read endpoint (14 days).
 */
@Singleton
public class CalibrationHistoryWorker implements Runnable {

    private static final Logger log = getLogger(CalibrationHistoryWorker.class);
    private static final int WINDOW_DAYS = 14;

    private final MarketModelResiduals residuals;

    @Inject
    public CalibrationHistoryWorker(Threading threading, MarketModelResiduals residuals) {
        this.residuals = residuals;
        // First capture 5 minutes after boot; then hourly.
        threading.botWorker().scheduleAtFixedRate(this, 5, 60, TimeUnit.MINUTES);
    }

    @Override
    public void run() {
        try {
            var summary = residuals.globalSummary(WINDOW_DAYS);
            query("""
                    INSERT INTO calibration_snapshot
                        (captured_at, window_days, sample_count, log_ratio_mean, log_ratio_sigma)
                    VALUES (:t, :w, :n, :mean, :sigma)
                    ON CONFLICT (captured_at) DO NOTHING
                    """)
                    .single(call().bind("t", Instant.now(), INSTANT_TIMESTAMP)
                            .bind("w", WINDOW_DAYS)
                            .bind("n", summary.count())
                            .bind("mean", summary.mean())
                            .bind("sigma", summary.sigma()))
                    .insert();
        } catch (Exception e) {
            log.warn("Calibration snapshot capture failed", e);
        }
    }
}
