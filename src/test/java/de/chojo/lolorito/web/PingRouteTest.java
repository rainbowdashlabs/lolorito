/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PingRouteTest extends RouteTestBase {

    @Test
    void pingReturnsOkWithoutSession() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/ping");
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"ok\":true");
        });
    }
}
