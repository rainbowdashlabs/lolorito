/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import io.javalin.http.Context;

/**
 * Boilerplate helpers for parsing query params — kept here so route
 * handlers stay one-line-per-param.
 */
final class QueryParams {
    private QueryParams() {}

    static Integer optInt(Context ctx, String name) {
        var raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int intOr(Context ctx, String name, int fallback, int lo, int hi) {
        var v = optInt(ctx, name);
        int chosen = v == null ? fallback : v;
        return Math.clamp(chosen, lo, hi);
    }

    static double doubleOr(Context ctx, String name, double fallback) {
        var raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
