/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeFilterResetRouteTest extends RouteTestBase {

    @Test
    void resetWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/me/filter", null, req -> {
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void salesHistoryWithoutHomeWorldReturns400() {
        var cookie = seedSession(60_101L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/items/1234/sales-history", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void salesHistoryWithExplicitHomeWorldReturnsArray() {
        var cookie = seedSession(60_102L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/items/1234/sales-history?home_world=66",
                    req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).startsWith("[");
        });
    }

    @Test
    void salesHistoryRejectsBadItemId() {
        var cookie = seedSession(60_103L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/items/not-a-number/sales-history?home_world=66",
                    req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void resetReturnsDefaults() {
        var cookie = seedSession(60_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/me/filter", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("refreshHours");
        });
    }
}
