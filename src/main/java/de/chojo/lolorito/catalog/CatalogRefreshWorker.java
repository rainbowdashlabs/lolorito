/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.catalog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.service.CraftDesynthLoader;
import de.chojo.lolorito.service.ItemCatalog;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Runtime catalog refresher. Guice constructs this as an eager
 * singleton at boot; the constructor schedules the refresh onto
 * {@link Threading#botWorker()} and returns immediately so app startup
 * never blocks on XIVAPI.
 *
 * <p>Each pass:
 * <ol>
 *   <li>Rebuilds the item / recipe / desynth records in memory.</li>
 *   <li>Reloads the DB seed via
 *       {@link CraftDesynthLoader#load(List, List)}.</li>
 *   <li>Ingests the item map into the in-memory
 *       {@link ItemCatalog}.</li>
 * </ol>
 * Config lives under {@code catalog.*}; disable with
 * {@code CATALOG_ENABLED=false} for CI / test containers.
 */
@Singleton
public class CatalogRefreshWorker implements Runnable {

    private static final Logger log = getLogger(CatalogRefreshWorker.class);

    private final File config;
    private final ItemCatalog itemCatalog;
    private final XivapiClient xivapi;

    @Inject
    public CatalogRefreshWorker(File config, Threading threading, ItemCatalog itemCatalog) {
        this.config = config;
        this.itemCatalog = itemCatalog;
        this.xivapi = new XivapiClient();
        if (!config.catalog().enabled()) {
            log.info("CatalogRefreshWorker disabled by config (catalog.enabled=false)");
            return;
        }
        int initialDelay = Math.max(0, config.catalog().initialDelaySeconds());
        int intervalHours = Math.max(1, config.catalog().intervalHours());
        long intervalSeconds = TimeUnit.HOURS.toSeconds(intervalHours);
        threading.botWorker().scheduleAtFixedRate(this, initialDelay, intervalSeconds, TimeUnit.SECONDS);
        log.info("CatalogRefreshWorker scheduled: first pass in {}s, then every {}h", initialDelay, intervalHours);
    }

    @Override
    public void run() {
        try {
            log.info("CatalogRefreshWorker: starting refresh pass");
            long start = System.currentTimeMillis();

            var icons = new IconCatalogBuilder(xivapi).build();
            if (!icons.isEmpty()) itemCatalog.ingest(icons);
            else log.warn("CatalogRefreshWorker: icon builder produced no rows, skipping ingest");

            var recipes = new RecipeCatalogBuilder(xivapi).build();
            if (recipes.isEmpty()) {
                log.warn("CatalogRefreshWorker: recipe builder produced no rows; skipping DB reload");
                return;
            }

            var desynth = new DesynthCatalogBuilder(xivapi).build(recipes);
            CraftDesynthLoader.load(recipes, desynth);

            log.info(
                    "CatalogRefreshWorker: refresh done in {}ms ({} icons, {} recipes, {} desynth sources)",
                    System.currentTimeMillis() - start,
                    icons.size(),
                    recipes.size(),
                    desynth.size());
        } catch (Exception e) {
            log.warn("CatalogRefreshWorker: refresh pass failed", e);
        }
    }
}
