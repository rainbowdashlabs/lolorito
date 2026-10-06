/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MeFilterRouteTest extends RouteTestBase {

    @Test
    void getReturnsDefaultRowWhenAbsent() {
        var cookie = seedSession(1_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/filter", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            var body = res.body().string();
            assertThat(body).contains("\"worldId\":-1");
            assertThat(body).contains("\"target\":\"DATA_CENTER\"");
        });
    }

    @Test
    void putUpdatesRowAndReturnsMergedShape() {
        var cookie = seedSession(1_101L);
        JavalinTest.test(app(), (server, client) -> {
            var body = Map.of("worldId", 66, "refreshHours", 12, "target", "REGION");
            var res = client.put("/api/v1/me/filter", body, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
            var text = res.body().string();
            assertThat(text).contains("\"worldId\":66");
            assertThat(text).contains("\"refreshHours\":12");
            assertThat(text).contains("\"target\":\"REGION\"");
        });
    }

    @Test
    void putWithGarbageBodyReturns400() {
        var cookie = seedSession(1_102L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.request("/api/v1/me/filter", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.put(HttpRequest.BodyPublishers.ofString("not json"));
            });
            assertThat(res.code()).isEqualTo(400);
            assertThat(res.body().string()).contains("invalid body");
        });
    }
}
