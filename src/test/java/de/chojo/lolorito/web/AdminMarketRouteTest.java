/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMarketRouteTest extends RouteTestBase {

    @Test
    void resetWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/api/v1/admin/market/reset-priors", Map.of(), req -> {
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void resetAsNonAdminReturns403() {
        var cookie = seedSession(80_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/api/v1/admin/market/reset-priors", Map.of(), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(403);
        });
    }

    @Test
    void resetAsAdminReturnsCounts() {
        var cookie = seedSession(TEST_ADMIN_USER_ID);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/api/v1/admin/market/reset-priors", Map.of(), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
            var body = res.body().string();
            assertThat(body).contains("models").contains("residuals").contains("snapshots");
        });
    }
}
