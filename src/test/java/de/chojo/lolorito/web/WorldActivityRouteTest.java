/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorldActivityRouteTest extends RouteTestBase {

    @Test
    void returnsTwentyFourHourBucketsForAKnownWorld() {
        var cookie = seedSession(1_300L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/worlds/66/sales/24h", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            var body = res.body().string();
            assertThat(body).contains("\"worldId\":66");
            assertThat(body).contains("\"hours\":[");
            assertThat(body.split("\"hourStart\"")).hasSize(25);
        });
    }

    @Test
    void rejectsAnUnknownWorld() {
        var cookie = seedSession(1_301L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/worlds/abc/sales/24h", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void requiresASession() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/worlds/66/sales/24h");
            assertThat(res.code()).isEqualTo(401);
        });
    }
}
