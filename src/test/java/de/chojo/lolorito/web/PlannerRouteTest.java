/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerRouteTest extends RouteTestBase {

    @Test
    void planWithHomeWorldReturnsEmptyPlan() {
        var cookie = seedSession(1_500L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/api/v1/plan", Map.of("homeWorld", 66), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("stops");
        });
    }

    @Test
    void planWithoutHomeWorldReturns400() {
        var cookie = seedSession(1_501L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/api/v1/plan", Map.of(), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
            assertThat(res.body().string()).contains("homeWorld");
        });
    }

    @Test
    void planWithUnknownHomeWorldReturns400() {
        var cookie = seedSession(1_502L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/api/v1/plan", Map.of("homeWorld", Integer.MAX_VALUE), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
            assertThat(res.body().string()).contains("unknown homeWorld");
        });
    }

    @Test
    void planWithGarbageBodyReturns400() {
        var cookie = seedSession(1_503L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.request("/api/v1/plan", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.post(HttpRequest.BodyPublishers.ofString("not json"));
            });
            assertThat(res.code()).isEqualTo(400);
            assertThat(res.body().string()).contains("invalid body");
        });
    }

    @Test
    void replanReturnsFreshPlan() {
        var cookie = seedSession(1_504L);
        JavalinTest.test(app(), (server, client) -> {
            var body = Map.of(
                    "homeWorld", 66,
                    "completedWorldIds", List.of(),
                    "spentBudget", 0,
                    "usedInventory", 0);
            var res = client.post("/api/v1/plan/replan", body, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("stops");
        });
    }

    @Test
    void replanWithoutHomeWorldReturns400() {
        var cookie = seedSession(1_505L);
        JavalinTest.test(app(), (server, client) -> {
            var body = Map.of(
                    "completedWorldIds", List.of(),
                    "spentBudget", 0,
                    "usedInventory", 0);
            var res = client.post("/api/v1/plan/replan", body, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
            assertThat(res.body().string()).contains("homeWorld");
        });
    }

    @Test
    void replanWithGarbageBodyReturns400() {
        var cookie = seedSession(1_506L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.request("/api/v1/plan/replan", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.post(HttpRequest.BodyPublishers.ofString("not json"));
            });
            assertThat(res.code()).isEqualTo(400);
            assertThat(res.body().string()).contains("invalid body");
        });
    }
}
