/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemRouteTest extends RouteTestBase {

    @Test
    void invalidItemIdReturns400() {
        var cookie = seedSession(1_400L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/items/not-a-number?home_world=66", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(400);
            assertThat(res.body().string()).contains("invalid item id");
        });
    }

    @Test
    void missingHomeWorldReturns400() {
        var cookie = seedSession(1_401L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/items/100", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(400);
            assertThat(res.body().string()).contains("home_world");
        });
    }

    @Test
    void validItemReturnsDetail() {
        var cookie = seedSession(1_402L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/items/100?home_world=66", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("itemId");
        });
    }

    @Test
    void unknownHomeWorldReturns404() {
        var cookie = seedSession(1_403L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/items/100?home_world=" + Integer.MAX_VALUE,
                    req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(404);
        });
    }

    @Test
    void hqQueryParamIsHonoured() {
        var cookie = seedSession(1_404L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/items/100?home_world=66&hq=true", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"hq\":true");
        });
    }
}
