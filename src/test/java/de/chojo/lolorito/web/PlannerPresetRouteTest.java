/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerPresetRouteTest extends RouteTestBase {

    @Test
    void saveThenListReturnsThePreset() {
        var cookie = seedSession(4_100L);
        JavalinTest.test(app(), (server, client) -> {
            var post = client.post(
                    "/api/v1/me/planner-presets",
                    java.util.Map.of("name", "quick flip", "params", "{\"budget\":500000}"),
                    req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            assertThat(post.code()).isEqualTo(201);
            var list = client.get("/api/v1/me/planner-presets", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(list.code()).isEqualTo(200);
            assertThat(list.body().string()).contains("\"name\":\"quick flip\"");
        });
    }

    @Test
    void saveWithoutNameReturns400() {
        var cookie = seedSession(4_101L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/api/v1/me/planner-presets", java.util.Map.of("params", "{}"), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void saveOversizedParamsReturns400() {
        var cookie = seedSession(4_102L);
        JavalinTest.test(app(), (server, client) -> {
            String huge = "\"" + "a".repeat(9_000) + "\"";
            var res =
                    client.post("/api/v1/me/planner-presets", java.util.Map.of("name", "big", "params", huge), req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void renameUpdatesTheName() {
        var cookie = seedSession(4_103L);
        JavalinTest.test(app(), (server, client) -> {
            var post =
                    client.post("/api/v1/me/planner-presets", java.util.Map.of("name", "old", "params", "{}"), req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            String id = post.body().string().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
            var put = client.put("/api/v1/me/planner-presets/" + id, java.util.Map.of("name", "new"), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(put.code()).isEqualTo(200);
            assertThat(put.body().string()).contains("\"name\":\"new\"");
        });
    }

    @Test
    void renameBadIdReturns400() {
        var cookie = seedSession(4_104L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put("/api/v1/me/planner-presets/not-a-uuid", java.util.Map.of("name", "x"), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void renameMissingReturns404() {
        var cookie = seedSession(4_105L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put(
                    "/api/v1/me/planner-presets/00000000-0000-0000-0000-000000000000",
                    java.util.Map.of("name", "x"),
                    req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            assertThat(res.code()).isEqualTo(404);
        });
    }

    @Test
    void deleteRemovesTheRow() {
        var cookie = seedSession(4_106L);
        JavalinTest.test(app(), (server, client) -> {
            var post =
                    client.post("/api/v1/me/planner-presets", java.util.Map.of("name", "tmp", "params", "{}"), req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            String id = post.body().string().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
            var del = client.delete("/api/v1/me/planner-presets/" + id, null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(del.code()).isEqualTo(204);
        });
    }

    @Test
    void deleteMissingReturns404() {
        var cookie = seedSession(4_107L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/me/planner-presets/00000000-0000-0000-0000-000000000000", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(404);
        });
    }

    @Test
    void listWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/planner-presets");
            assertThat(res.code()).isEqualTo(401);
        });
    }
}
