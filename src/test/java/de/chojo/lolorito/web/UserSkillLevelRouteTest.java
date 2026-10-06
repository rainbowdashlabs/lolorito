/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserSkillLevelRouteTest extends RouteTestBase {

    @Test
    void listWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/skills");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void listReturnsFullyNormalisedShape() {
        var cookie = seedSession(30_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/skills", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            String body = res.body().string();
            assertThat(body).contains("craft").contains("desynth").contains("blacksmith");
        });
    }

    @Test
    void putUpsertsAndReturnsUpdatedMap() {
        var cookie = seedSession(30_101L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put(
                    "/api/v1/me/skills", Map.of("kind", "craft", "className", "blacksmith", "level", 85), req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"blacksmith\":85");
        });
    }

    @Test
    void putWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put("/api/v1/me/skills", Map.of("kind", "craft"), req -> {
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void putRejectsInvalidBody() {
        var cookie = seedSession(30_103L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put("/api/v1/me/skills", Map.of("garbage", 1), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void putRejectsUnknownClass() {
        var cookie = seedSession(30_104L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put(
                    "/api/v1/me/skills", Map.of("kind", "craft", "className", "gunsmith", "level", 10), req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void putRejectsUnknownKind() {
        var cookie = seedSession(30_102L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put(
                    "/api/v1/me/skills", Map.of("kind", "gathering", "className", "miner", "level", 10), req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            assertThat(res.code()).isEqualTo(400);
        });
    }
}
