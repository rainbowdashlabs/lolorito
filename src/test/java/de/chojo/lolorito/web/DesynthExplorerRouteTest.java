/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DesynthExplorerRouteTest extends RouteTestBase {

    @Test
    void withoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/desynth");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void withoutHomeWorldReturns400() {
        var cookie = seedSession(50_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/desynth", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void withExplicitHomeWorldReturns200() {
        var cookie = seedSession(50_101L);
        JavalinTest.test(app(), (server, client) -> {
            // Odin's world id in the standard Universalis mapping is 66.
            var res = client.get("/api/v1/desynth?homeWorld=66", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).startsWith("[");
        });
    }

    @Test
    void classFilterAndRegionScopeAreAccepted() {
        var cookie = seedSession(50_102L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/desynth?homeWorld=66&class=Carpenter,%20alc,,&minLevel=10&scope=region",
                    req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).startsWith("[");
        });
    }
}
