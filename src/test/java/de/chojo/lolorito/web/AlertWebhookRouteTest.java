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

class AlertWebhookRouteTest extends RouteTestBase {

    @Test
    void getWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/alert-webhook");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void getReturnsEmptyWhenNotSet() {
        var cookie = seedSession(70_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/alert-webhook", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"url\":\"\"");
        });
    }

    @Test
    void putSavesAndReadsBack() {
        var cookie = seedSession(70_101L);
        JavalinTest.test(app(), (server, client) -> {
            var put = client.put("/api/v1/me/alert-webhook", Map.of("url", "https://example.test/hook"), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(put.code()).isEqualTo(200);
            var get = client.get("/api/v1/me/alert-webhook", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(get.body().string()).contains("example.test");
        });
    }

    @Test
    void putRejectsNonHttpUrl() {
        var cookie = seedSession(70_102L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put("/api/v1/me/alert-webhook", Map.of("url", "javascript:alert(1)"), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void putBlankClears() {
        var cookie = seedSession(70_103L);
        JavalinTest.test(app(), (server, client) -> {
            var put = client.put("/api/v1/me/alert-webhook", Map.of("url", ""), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(put.code()).isEqualTo(200);
            assertThat(put.body().string()).contains("\"url\":\"\"");
        });
    }

    @Test
    void putWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put("/api/v1/me/alert-webhook", Map.of("url", "https://example.test"), req -> {
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(401);
        });
    }
}
