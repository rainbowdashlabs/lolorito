/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterRouteTest extends RouteTestBase {

    @Test
    void getBeforeLinkReturns404() {
        var cookie = seedSession(6_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/character", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(404);
        });
    }

    @Test
    void getWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/character");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void refreshWithoutBodyReturns400() {
        var cookie = seedSession(6_101L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.request("/api/v1/me/character/refresh", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.post(java.net.http.HttpRequest.BodyPublishers.ofString("not json"));
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void refreshWithoutIdReturns400() {
        var cookie = seedSession(6_102L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/api/v1/me/character/refresh", java.util.Map.of(), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void refreshSucceedsAndLinksTheProfile() {
        var cookie = seedSession(6_103L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/api/v1/me/character/refresh", java.util.Map.of("lodestoneId", 1234567L), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
            String body = res.body().string().replaceAll("\\s+", "");
            assertThat(body).contains("\"name\":\"TestChar\"");
        });
    }

    @Test
    void getAfterRefreshReturnsProfile() {
        var cookie = seedSession(6_105L);
        JavalinTest.test(app(), (server, client) -> {
            client.post("/api/v1/me/character/refresh", java.util.Map.of("lodestoneId", 1234567L), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            var res = client.get("/api/v1/me/character", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"lodestoneId\":1234567");
        });
    }

    @Test
    void unlinkAfterRefreshReturns204() {
        var cookie = seedSession(6_106L);
        JavalinTest.test(app(), (server, client) -> {
            client.post("/api/v1/me/character/refresh", java.util.Map.of("lodestoneId", 1234567L), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            var res = client.delete("/api/v1/me/character", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(204);
        });
    }

    @Test
    void unlinkBeforeLinkReturns404() {
        var cookie = seedSession(6_104L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/me/character", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(404);
        });
    }
}
