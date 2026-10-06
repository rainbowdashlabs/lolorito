/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the two new dashboard-only endpoints on CalibrationRoutes. */
class DashboardRouteTest extends RouteTestBase {

    @Test
    void calibrationHistoryReturnsArray() {
        var cookie = seedSession(6_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/calibration/history?hours=24", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).startsWith("[");
        });
    }

    @Test
    void statsReturnsCapabilityJson() {
        var cookie = seedSession(6_101L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/dashboard/stats", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("totalModels").contains("databaseBytes");
        });
    }
}
