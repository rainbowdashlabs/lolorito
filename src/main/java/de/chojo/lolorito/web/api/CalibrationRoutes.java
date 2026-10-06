/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import com.google.inject.Inject;
import de.chojo.lolorito.service.CalibrationService;
import de.chojo.lolorito.web.Routes;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;

/**
 * Dashboard read routes:
 * <ul>
 *   <li>{@code GET /api/v1/calibration} — point-in-time summary</li>
 *   <li>{@code GET /api/v1/calibration/history?hours=…} — hourly rolling
 *       series for the echarts trend chart</li>
 *   <li>{@code GET /api/v1/dashboard/stats} — capability + perf headline</li>
 * </ul>
 */
public class CalibrationRoutes implements Routes {

    private final CalibrationService calibration;

    @Inject
    public CalibrationRoutes(CalibrationService calibration) {
        this.calibration = calibration;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/v1/calibration", this::snapshot);
        routes.get("/api/v1/calibration/history", this::history);
        routes.get("/api/v1/calibration/shelf", this::shelf);
        routes.get("/api/v1/calibration/keys", this::keys);
        routes.get("/api/v1/dashboard/stats", this::stats);
    }

    private void keys(Context ctx) {
        int window = QueryParams.intOr(ctx, "window", 14, 1, 60);
        int limit = QueryParams.intOr(ctx, "limit", 25, 1, 200);
        ctx.json(calibration.worstKeys(window, limit));
    }

    private void shelf(Context ctx) {
        int window = QueryParams.intOr(ctx, "window", 14, 1, 60);
        int limit = QueryParams.intOr(ctx, "limit", 2000, 100, 10000);
        ctx.json(calibration.shelfTime(window, limit));
    }

    private void snapshot(Context ctx) {
        Integer window = QueryParams.optInt(ctx, "window");
        ctx.json(window == null ? calibration.recent() : calibration.lastNDays(window));
    }

    private void history(Context ctx) {
        int hours = QueryParams.intOr(ctx, "hours", 24 * 7, 1, 24 * 90);
        ctx.json(calibration.history(hours));
    }

    private void stats(Context ctx) {
        ctx.json(calibration.capabilityStats());
    }
}
