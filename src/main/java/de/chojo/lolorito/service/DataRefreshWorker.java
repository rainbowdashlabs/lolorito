/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.repository.ListingEpisodes;
import de.chojo.lolorito.repository.Listings;
import de.chojo.lolorito.repository.PerfMetrics;
import de.chojo.lolorito.repository.Sales;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Scheduled sales-clean + materialised-view refresher. Guice constructs
 * this as an eager singleton at boot and schedules itself onto
 * {@code Threading.botWorker()} — no external {@code create(...)} needed.
 */
@Singleton
public class DataRefreshWorker implements Runnable {
    private static final Logger log = getLogger(DataRefreshWorker.class);
    private static final List<String> VIEWS = List.of(
            "world_item_listings",
            "world_item_sales",
            "world_item_views",
            "world_sales", // depends on world_item_sales
            "world_views", // depends on world_item_views
            "world_item_popularity", // depends on all other views
            "world_items" // depends on all other views
            );

    /** Ended episodes older than this feed neither calibration nor detectors — match the sales retention. */
    private static final int EPISODE_RETENTION_DAYS = 60;

    /** View counters only feed the 7-day {@code world_item_views} window; one spare day covers the boundary. */
    private static final int VIEW_RETENTION_DAYS = 8;

    private final Sales sales;
    private final ListingEpisodes episodes;
    private final Listings listings;
    private final PerfMetrics perfMetrics;

    @Inject
    public DataRefreshWorker(Threading threading, Sales sales, ListingEpisodes episodes, Listings listings,
                             PerfMetrics perfMetrics) {
        this.sales = sales;
        this.episodes = episodes;
        this.listings = listings;
        this.perfMetrics = perfMetrics;
        threading.botWorker().scheduleAtFixedRate(this, 1, 5, TimeUnit.MINUTES);
    }

    @Override
    public void run() {
        int clean = sales.clean();
        log.debug("Deleted {} sales", clean);
        int episodeClean = episodes.clean(EPISODE_RETENTION_DAYS);
        log.debug("Deleted {} ended listing episodes / undercut events", episodeClean);
        int viewClean = listings.cleanViews(VIEW_RETENTION_DAYS);
        log.debug("Deleted {} listing view counters", viewClean);
        log.debug("Refreshing views");
        long totalStart = System.currentTimeMillis();
        for (String view : VIEWS) {
            long start = System.currentTimeMillis();
            // View name comes from a static allowlist above, so string
            // interpolation is safe here — no user input in play.
            query("REFRESH MATERIALIZED VIEW " + view).single(call()).update();
            long ms = System.currentTimeMillis() - start;
            log.debug("View {} refreshed. Took {} ms", view, ms);
            try {
                perfMetrics.record("view_refresh_ms", ms, view);
            } catch (Exception e) {
                log.debug("perf record failed", e);
            }
        }
        try {
            perfMetrics.record("view_refresh_total_ms", System.currentTimeMillis() - totalStart, null);
        } catch (Exception e) {
            log.debug("perf record failed", e);
        }
    }
}
