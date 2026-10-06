/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetainerRouteTest extends RouteTestBase {

    @Test
    void listWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/retainers");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void createAndListRoundTrip() {
        var cookie = seedSession(7_100L);
        JavalinTest.test(app(), (server, client) -> {
            var post = client.post(
                    "/api/v1/me/retainers", java.util.Map.of("retainerName", "Wolfe", "worldId", 66), req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            assertThat(post.code()).isEqualTo(201);

            var list = client.get("/api/v1/me/retainers", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(list.code()).isEqualTo(200);
            assertThat(list.body().string()).contains("\"retainerName\":\"Wolfe\"");
        });
    }

    @Test
    void createWithoutNameReturns400() {
        var cookie = seedSession(7_101L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/api/v1/me/retainers", java.util.Map.of("worldId", 66), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void deleteMissingReturns404() {
        var cookie = seedSession(7_102L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.request("/api/v1/me/retainers", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.delete(java.net.http.HttpRequest.BodyPublishers.ofString(
                        "{\"retainerName\":\"ghost\",\"worldId\":66}"));
            });
            assertThat(res.code()).isEqualTo(404);
        });
    }

    @Test
    void deleteWithGarbageBodyReturns400() {
        var cookie = seedSession(7_104L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.request("/api/v1/me/retainers", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.delete(java.net.http.HttpRequest.BodyPublishers.ofString("nope"));
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void createWithGarbageBodyReturns400() {
        var cookie = seedSession(7_105L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.request("/api/v1/me/retainers", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.post(java.net.http.HttpRequest.BodyPublishers.ofString("garbage"));
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void suggestReturns200AndArray() {
        var cookie = seedSession(7_106L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/retainer-suggestions?world=66&q=wo", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).startsWith("[");
        });
    }

    @Test
    void suggestWithoutWorldReturns400() {
        var cookie = seedSession(7_107L);
        JavalinTest.test(app(), (server, client) -> {
            var res =
                    client.get("/api/v1/retainer-suggestions?q=wo", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void suggestWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/retainer-suggestions?world=66");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void ownedListingsIsEmptyBeforeDeclaring() {
        var cookie = seedSession(7_103L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/listings", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).isEqualTo("[]");
        });
    }
}
