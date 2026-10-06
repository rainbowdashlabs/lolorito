/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.repository.MarketModelResiduals;
import de.chojo.lolorito.repository.MarketModels;
import de.chojo.universalis.entities.Sale;
import de.chojo.universalis.worlds.World;
import org.slf4j.Logger;

import java.util.Collection;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Residual writer. For each Universalis-observed sale we look up the
 * current {@code market_model} row and store the log-ratio between the
 * observed price and the model's expected price. The fitter reads these
 * residuals back as a Bayesian shrinkage prior on the next refit.
 *
 * <p>Sales for keys we haven't fitted yet are ignored — no prediction
 * means nothing to residual against.
 */
@Singleton
public class ResidualRecorder {
    private static final Logger log = getLogger(ResidualRecorder.class);

    private final MarketModels models;
    private final MarketModelResiduals residuals;

    @Inject
    public ResidualRecorder(MarketModels models, MarketModelResiduals residuals) {
        this.models = models;
        this.residuals = residuals;
    }

    /**
     * Record one residual per sale in {@code sales}. Silent on model miss.
     */
    public void record(World world, int itemId, Collection<Sale> sales) {
        for (Sale sale : sales) {
            recordOne(world.id(), itemId, sale);
        }
    }

    private void recordOne(int worldId, int itemId, Sale sale) {
        try {
            var model = models.find(worldId, itemId, sale.hq()).orElse(null);
            if (model == null) return;
            // Residual against the MEDIAN (exp(mu)), not the log-normal mean
            // (exp(mu + sigma^2/2)): E[log(price)] = mu, so the median gives
            // zero-mean residuals under a calibrated model. Against the mean
            // every key carries a structural -sigma^2/2 offset that the
            // shrinkage prior would then "correct" into a real bias.
            double expected = model.price().medianPrice();
            int actual = sale.price().pricePerUnit();
            if (actual <= 0 || expected <= 0.0) return;
            double logRatio = Math.log((double) actual / expected);
            var observedAt = sale.timestamp();
            residuals.insert(itemId, worldId, sale.hq(), observedAt, logRatio);
        } catch (Exception e) {
            log.warn("Residual record failed for (item={}, world={}): {}", itemId, worldId, e.getMessage());
        }
    }
}
