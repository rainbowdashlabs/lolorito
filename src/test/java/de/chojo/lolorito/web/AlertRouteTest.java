/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRouteTest extends RouteTestBase {

    private static Map<String, Object> body(
            int itemId, Integer worldId, Integer dc, Boolean hq, String kind, int threshold) {
        var m = new HashMap<String, Object>();
        m.put("itemId", itemId);
        m.put("worldId", worldId);
        m.put("dataCenterId", dc);
        m.put("hq", hq);
        m.put("kind", kind);
        m.put("threshold", threshold);
        m.put("cooldownMinutes", 30);
        return m;
    }

    @Test
    void createAndListReturnsRule() {
        var cookie = seedSession(5_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/api/v1/alerts", body(100, 66, null, false, "price_below", 500), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(201);
            assertThat(res.body().string()).contains("\"kind\":\"price_below\"");

            var list = client.get("/api/v1/alerts", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(list.code()).isEqualTo(200);
            assertThat(list.body().string()).contains("\"itemId\":100");
        });
    }

    @Test
    void createRejectsBothScopesAsBadRequest() {
        var cookie = seedSession(5_101L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/api/v1/alerts", body(100, 66, 7, false, "price_below", 500), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void createGarbageBodyReturns400() {
        var cookie = seedSession(5_102L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.request("/api/v1/alerts", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.post(HttpRequest.BodyPublishers.ofString("not json"));
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void patchTogglesEnabled() {
        var cookie = seedSession(5_103L);
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post("/api/v1/alerts", body(100, 66, null, false, "price_below", 500), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            String id = extractId(created.body().string());
            var res = client.patch("/api/v1/alerts/" + id, Map.of("enabled", false), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"enabled\":false");
        });
    }

    @Test
    void patchRequiresEnabledField() {
        var cookie = seedSession(5_104L);
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post("/api/v1/alerts", body(100, 66, null, false, "price_below", 500), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            String id = extractId(created.body().string());
            var res = client.patch("/api/v1/alerts/" + id, Map.of(), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void patchOnMissingRuleReturns404() {
        var cookie = seedSession(5_105L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.patch("/api/v1/alerts/" + UUID.randomUUID(), Map.of("enabled", false), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(404);
        });
    }

    @Test
    void patchWithGarbageBodyReturns400() {
        var cookie = seedSession(5_106L);
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post("/api/v1/alerts", body(100, 66, null, false, "price_below", 500), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            String id = extractId(created.body().string());
            var res = client.request("/api/v1/alerts/" + id, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.patch(HttpRequest.BodyPublishers.ofString("not json"));
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void deleteRejectsNonOwner() {
        var owner = seedSession(5_107L);
        var visitor = seedSession(5_108L);
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post("/api/v1/alerts", body(100, 66, null, false, "price_below", 500), req -> {
                req.header("Cookie", cookieHeader(owner));
                req.header("X-Requested-With", "lolorito");
            });
            String id = extractId(created.body().string());
            var res = client.delete("/api/v1/alerts/" + id, null, req -> {
                req.header("Cookie", cookieHeader(visitor));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(404);
        });
    }

    @Test
    void deleteByOwnerReturns204() {
        var cookie = seedSession(5_109L);
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post("/api/v1/alerts", body(100, 66, null, false, "price_below", 500), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            String id = extractId(created.body().string());
            var res = client.delete("/api/v1/alerts/" + id, null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(204);
        });
    }

    @Test
    void invalidUuidReturns400() {
        var cookie = seedSession(5_110L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/alerts/not-a-uuid", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    // Helpers --------------------------------------------------------------

    private static String extractId(String body) {
        int i = body.indexOf("\"id\":\"");
        int start = i + "\"id\":\"".length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
