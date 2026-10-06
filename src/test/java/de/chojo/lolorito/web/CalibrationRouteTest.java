/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalibrationRouteTest extends RouteTestBase {

    @Test
    void snapshotWithoutObservationsReturnsZeroCountAndNoteMessage() {
        var cookie = seedSession(3_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/calibration", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            var body = res.body().string();
            assertThat(body).contains("\"sampleCount\":0");
            assertThat(body).contains("\"windowDays\":14");
            assertThat(body.toLowerCase()).contains("no sales");
        });
    }

    @Test
    void windowQueryParamHonoured() {
        var cookie = seedSession(3_101L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/calibration?window=3", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"windowDays\":3");
        });
    }

    @Test
    void openToUnauthenticatedCallers() {
        // Calibration + dashboard/stats are operational data — no user tied
        // to the response, so they're on the open-endpoint allowlist and
        // must return 200 without a session cookie.
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/calibration");
            assertThat(res.code()).isEqualTo(200);
        });
    }
}
