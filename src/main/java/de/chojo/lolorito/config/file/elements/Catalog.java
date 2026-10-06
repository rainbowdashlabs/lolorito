/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.config.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

/**
 * Runtime knobs for the async catalog refresh worker (icons + recipes +
 * desynth pulled from XIVAPI on a schedule and reloaded into the DB).
 * The build-time {@code refreshCatalog} Gradle task doesn't read these —
 * it uses its own defaults from {@code CatalogRefreshCli}.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
@OverwritePrefix("CATALOG")
public class Catalog {

    /**
     * Whether the runtime worker runs at all. Disable in constrained
     * environments (CI, ephemeral test containers) where the background
     * fetch just wastes cycles.
     */
    @Overwrite(env = @Env)
    private boolean enabled = true;

    /**
     * Seconds to wait after boot before the first XIVAPI refresh. Kept
     * short so a fresh clone (no classpath seed → empty DB) gets its
     * first population quickly; the classpath cold seed (when present)
     * is loaded on {@code Threading.botWorker()} in parallel by
     * {@link de.chojo.lolorito.core.DatabaseBootstrap}, so this delay
     * primarily governs when we start reaching to XIVAPI.
     */
    @Overwrite(env = @Env)
    private int initialDelaySeconds = 15;

    /** How often the worker re-runs the pipeline after the first pass. */
    @Overwrite(env = @Env)
    private int intervalHours = 24;

    public boolean enabled() {
        return enabled;
    }

    public int initialDelaySeconds() {
        return initialDelaySeconds;
    }

    public int intervalHours() {
        return intervalHours;
    }
}
