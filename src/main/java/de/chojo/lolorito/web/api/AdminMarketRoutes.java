/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.entity.Session;
import de.chojo.lolorito.service.CalibrationService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Map;

/**
 * Ops surface for the market fitter. Owner-only.
 *
 * <ul>
 *   <li>{@code POST /api/v1/admin/market/reset-priors} — blow away every
 *       fitted model, every recorded residual, and every stored
 *       calibration snapshot. The next fitter cycle starts from scratch.
 *       Use when we've fed the fitter bad data (schema migration,
 *       corrupt Universalis ingest) and need a clean baseline.</li>
 * </ul>
 */
public class AdminMarketRoutes implements Routes {

    private final CalibrationService calibration;
    private final File config;

    @Inject
    public AdminMarketRoutes(CalibrationService calibration, File config) {
        this.calibration = calibration;
        this.config = config;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.post("/api/v1/admin/market/reset-priors", this::resetPriors);
    }

    private void resetPriors(Context ctx) {
        Session current = SessionResolver.sessionOf(ctx);
        if (current == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        if (!config.baseSettings().isOwner(current.discordUserId())) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("error", "forbidden"));
            return;
        }
        var summary = calibration.resetAll();
        ctx.json(Map.of(
                "models", summary.models(),
                "residuals", summary.residuals(),
                "snapshots", summary.snapshots()));
    }
}
