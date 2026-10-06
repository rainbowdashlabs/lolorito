/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemNamesRouteTest extends RouteTestBase {

    @Test
    void withoutIdsReturnsEmptyObject() {
        var cookie = seedSession(9_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/item-names", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).isEqualTo("{}");
        });
    }

    @Test
    void garbageIdsAreIgnored() {
        var cookie = seedSession(9_101L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/item-names?ids=abc,-1,0", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).isEqualTo("{}");
        });
    }
}
