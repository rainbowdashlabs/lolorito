/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorldsRouteTest extends RouteTestBase {

    @Test
    void listsHierarchyForAuthenticatedUser() {
        var cookie = seedSession(1_200L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/worlds", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            var body = res.body().string();
            assertThat(body).contains("Europe");
            assertThat(body).contains("Light");
        });
    }
}
