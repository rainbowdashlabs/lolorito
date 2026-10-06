/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionListRouteTest extends RouteTestBase {

    @Test
    void listWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/sessions");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void listReturnsCurrentSession() {
        var cookie = seedSession(8_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/sessions", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"current\":true");
        });
    }

    @Test
    void revokeUnknownReturns404() {
        var cookie = seedSession(8_101L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/me/sessions/does-not-exist", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(404);
        });
    }

    @Test
    void revokeAllReturnsRemovedCount() {
        var cookie = seedSession(8_102L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/me/sessions", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("removed");
        });
    }

    @Test
    void revokeWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/me/sessions/whatever", null, req -> {
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void revokeCurrentReturns204() {
        var cookie = seedSession(8_103L);
        JavalinTest.test(app(), (server, client) -> {
            var list = client.get("/api/v1/me/sessions", req -> req.header("Cookie", cookieHeader(cookie)));
            String body = list.body().string();
            // extract the id of the first session
            String id = body.replaceAll(".*\\[\\{\"id\":\"([^\"]+)\".*", "$1");
            var del = client.delete("/api/v1/me/sessions/" + id, null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(del.code()).isEqualTo(204);
        });
    }

    @Test
    void revokeAllWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/me/sessions", null, req -> req.header("X-Requested-With", "lolorito"));
            assertThat(res.code()).isEqualTo(401);
        });
    }
}
