/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemCatalogRouteTest extends RouteTestBase {

    @Test
    void catalogEndpointReturnsJson() {
        var cookie = seedSession(20_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/item-catalog", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).startsWith("{");
        });
    }

    @Test
    void iconEndpointRejectsBadId() {
        var cookie = seedSession(20_101L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/item-icon/not-a-number", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void iconEndpointReturns404ForUnknownItem() {
        var cookie = seedSession(20_102L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/item-icon/999999999", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(404);
        });
    }
}
