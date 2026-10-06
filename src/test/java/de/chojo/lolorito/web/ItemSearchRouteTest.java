/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemSearchRouteTest extends RouteTestBase {

    @Test
    void unauthenticatedSearchIsRejected() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/item-search?q=iron");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void emptyQueryReturnsEmptyJson() {
        var cookie = seedSession(2_400L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/item-search", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("[").contains("]");
        });
    }

    @Test
    void nonEmptyQueryReturnsJsonArray() {
        var cookie = seedSession(2_401L);
        JavalinTest.test(app(), (server, client) -> {
            // The supplier is empty in the test env, so we don't get hits — but
            // the route still returns a JSON array with a 200 status.
            var res =
                    client.get("/api/v1/item-search?q=iron&limit=5", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("[").contains("]");
        });
    }
}
