/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.config.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

import java.util.Collections;
import java.util.Map;

/**
 * Knobs for the market-model fitter and the value engine. Everything is
 * env-overridable so ops can tune under load without a redeploy.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
@OverwritePrefix("VALUE")
public class Value {
    /**
     * Market-board sale tax; taken off gross proceeds before EV is reported.
     * Used as the fallback when {@link #mbTaxByDataCenter} has no entry for
     * the caller's DC.
     */
    @Overwrite(env = @Env)
    private double mbTax = 0.05;

    /**
     * Optional per-data-center overrides. Keys are Universalis DC ids (e.g.
     * Light = 7, Chaos = 6); values are the fractional tax (5 % → 0.05).
     * Any DC not listed uses {@link #mbTax}. Not env-overridable because
     * shell env vars don't map cleanly to a map — edit config.yaml.
     */
    private Map<Integer, Double> mbTaxByDataCenter = Collections.emptyMap();

    /**
     * How often {@code MarketModelWorker} refits every keyed model.
     */
    @Overwrite(env = @Env)
    private int modelRefreshMinutes = 60;

    /**
     * Below this many weighted samples the key is marked {@code insufficient}.
     */
    @Overwrite(env = @Env)
    private int minSamplesPerKey = 5;

    /**
     * Half-life of the recency weight applied to sales in the price fit.
     */
    @Overwrite(env = @Env)
    private int priceHalfLifeDays = 7;

    /**
     * Window (in days) used to compute rolling sales & rate anchors.
     * Sales older than this contribute nothing to the fit.
     */
    @Overwrite(env = @Env)
    private int rateWindowDays = 14;

    /**
     * Below this median seconds-to-undercut a world/item is flagged as
     * bot-undercut. Used later once listing history is tracked; kept in
     * config now so downstream code can read a stable value.
     */
    @Overwrite(env = @Env)
    private int undercutBotThresholdSeconds = 60;

    /**
     * Owners without a listing in this many days are counted as ghosts.
     * Read by future listing-history-based analysis; kept here for parity.
     */
    @Overwrite(env = @Env)
    private int ghostListingWindowDays = 14;

    /**
     * How many keys the worker fits per fan-out batch. Keeps per-cycle DB
     * pressure bounded even when the sales table is dense. With ~250k
     * active keys across four regions, 500/hour never drained the
     * never-fitted backlog — 2000 clears a home world's ~4k keys in a
     * couple of cycles and the full backlog in days.
     */
    @Overwrite(env = @Env)
    private int refitBatchSize = 2000;

    /**
     * TTL for cached responses on the hot read paths (offers, plan, item
     * detail). Matches the SPA's own poll cadence — set to zero to
     * disable.
     */
    @Overwrite(env = @Env)
    private int responseCacheSeconds = 30;

    /**
     * Maximum cache entries per hot path. Keeps memory bounded when many
     * distinct users hit the same node.
     */
    @Overwrite(env = @Env)
    private int responseCacheMaxSize = 1000;
    /**
     * How many days of residuals contribute to the shrinkage prior on
     * the next fit. Older observations are ignored so the model tracks
     * regime changes.
     */
    @Overwrite(env = @Env)
    private int residualWindowDays = 14;
    /**
     * Strength of the shrinkage prior — {@code n / (n + k)} weights the
     * observed log ratio when shifting {@code mu}. With k = 20 a key with
     * 20 residual samples shrinks halfway toward the observed ratio.
     */
    @Overwrite(env = @Env)
    private int residualShrinkagePriorK = 20;

    /**
     * Wall-clock seconds to craft one unit (macro time, roughly). Charged
     * as full-attention time in the craft valuation's denominator so a
     * 99-batch or a deep chain stops being "free" per attention-hour.
     */
    @Overwrite(env = @Env)
    private double craftSecondsPerUnit = 5.0;

    /**
     * How stale a world's listing snapshot may be before cheapest-price
     * lookups (craft ingredients, desynth buys) stop trusting it. The
     * offers feed has its own per-request freshness; this guards the paths
     * that used to have none — a listing bought out days ago must not
     * anchor a recommendation.
     */
    @Overwrite(env = @Env)
    private int listingFreshnessHours = 24;

    /**
     * How often the alert-rule scanner probes for threshold crossings.
     * Higher frequency catches short-lived listings but adds DB / Discord
     * load; the default of 5 minutes matches the listings refresh cadence.
     */
    @Overwrite(env = @Env)
    private int alertScanIntervalMinutes = 5;

    /**
     * Optional webhook URL that fired alerts also POST to (JSON body).
     * Empty by default — Discord DM is the primary sink. When set, both
     * dispatchers run so operators can pipe alerts into their own
     * monitoring.
     */
    @Overwrite(env = @Env)
    private String alertWebhookUrl = "";

    public int alertScanIntervalMinutes() {
        return alertScanIntervalMinutes;
    }

    public String alertWebhookUrl() {
        return alertWebhookUrl;
    }

    public double mbTax() {
        return mbTax;
    }

    public Map<Integer, Double> mbTaxByDataCenter() {
        return mbTaxByDataCenter;
    }

    /**
     * Effective tax for {@code dataCenterId} — override if present, else {@link #mbTax}.
     */
    public double mbTaxFor(int dataCenterId) {
        Double override = mbTaxByDataCenter.get(dataCenterId);
        return override != null ? override : mbTax;
    }

    public int modelRefreshMinutes() {
        return modelRefreshMinutes;
    }

    public int minSamplesPerKey() {
        return minSamplesPerKey;
    }

    public int priceHalfLifeDays() {
        return priceHalfLifeDays;
    }

    public int rateWindowDays() {
        return rateWindowDays;
    }

    public int undercutBotThresholdSeconds() {
        return undercutBotThresholdSeconds;
    }

    public int ghostListingWindowDays() {
        return ghostListingWindowDays;
    }

    public int refitBatchSize() {
        return refitBatchSize;
    }

    public int responseCacheSeconds() {
        return responseCacheSeconds;
    }

    public int responseCacheMaxSize() {
        return responseCacheMaxSize;
    }

    public int residualWindowDays() {
        return residualWindowDays;
    }

    public int residualShrinkagePriorK() {
        return residualShrinkagePriorK;
    }

    public double craftSecondsPerUnit() {
        return craftSecondsPerUnit;
    }

    public int listingFreshnessHours() {
        return listingFreshnessHours;
    }
}
