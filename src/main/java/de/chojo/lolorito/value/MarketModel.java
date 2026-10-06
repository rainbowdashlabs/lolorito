/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import java.time.Instant;

/**
 * The fitted per-(item, world, hq) model that {@link ValueEngine} consumes.
 * Rows in {@code lolorito.market_model} round-trip through this record.
 *
 * @param sampleCount    raw count of sales that fed the fit
 * @param sufficient     true when the model has enough weighted evidence to
 *                       be trusted downstream; {@code ValueEngine} refuses
 *                       to score against a model where this is false
 * @param pooled         true when the fit fell back to DC-pooled sales
 *                       because the key alone was too thin — treat as
 *                       lower-confidence
 * @param lambdaUndercut undercuts/hour, measured from {@code undercut_event}
 *                       rows the listing differ records
 * @param ghostFraction  share of the current open stack that has sat
 *                       unmoved beyond the ghost window
 */
public record MarketModel(
        int itemId,
        int worldId,
        boolean hq,
        PriceDistribution price,
        SaleRate saleRate,
        double lambdaUndercut,
        double ghostFraction,
        int sampleCount,
        boolean sufficient,
        boolean pooled,
        Instant fittedAt) {}
