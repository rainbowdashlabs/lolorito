/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end coverage for {@code /api/v1/me/retainer-sales}. */
class RetainerSaleRouteTest extends RouteTestBase {

    @Test
    void createReturns201AndListReadsBack() {
        var cookie = seedSession(5_500L);
        JavalinTest.test(app(), (server, client) -> {
            var body = Map.of(
                    "itemId", 5057,
                    "worldId", 66,
                    "hq", false,
                    "unitPrice", 400,
                    "quantity", 5,
                    "retainerName", "Wolfe");
            var post = client.post("/api/v1/me/retainer-sales", body, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(post.code()).isEqualTo(201);

            var list = client.get("/api/v1/me/retainer-sales", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(list.code()).isEqualTo(200);
            String bodyStr = list.body().string();
            assertThat(bodyStr).contains("\"retainerName\":\"Wolfe\"");
        });
    }

    @Test
    void createRejectsMissingFields() {
        var cookie = seedSession(5_501L);
        JavalinTest.test(app(), (server, client) -> {
            // Missing quantity + unitPrice.
            var body = Map.of("itemId", 5057, "worldId", 66);
            var post = client.post("/api/v1/me/retainer-sales", body, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(post.code()).isEqualTo(400);
        });
    }

    @Test
    void createRejectsNonPositiveValues() {
        var cookie = seedSession(5_502L);
        JavalinTest.test(app(), (server, client) -> {
            var body = Map.of("itemId", 5057, "worldId", 66, "hq", false, "unitPrice", 0, "quantity", 1);
            var post = client.post("/api/v1/me/retainer-sales", body, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(post.code()).isEqualTo(400);
        });
    }

    @Test
    void listWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/retainer-sales");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void deleteRemovesTheRow() {
        var cookie = seedSession(5_503L);
        JavalinTest.test(app(), (server, client) -> {
            var body = Map.of("itemId", 5057, "worldId", 66, "hq", false, "unitPrice", 400, "quantity", 1);
            var post = client.post("/api/v1/me/retainer-sales", body, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(post.code()).isEqualTo(201);
            long id = Long.parseLong(post.body().string().replaceAll(".*\"id\":(\\d+).*", "$1"));

            var del = client.delete("/api/v1/me/retainer-sales/" + id, null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(del.code()).isEqualTo(204);
        });
    }

    @Test
    void deleteWithNonNumericIdReturns400() {
        var cookie = seedSession(5_505L);
        JavalinTest.test(app(), (server, client) -> {
            var del = client.delete("/api/v1/me/retainer-sales/not-a-number", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(del.code()).isEqualTo(400);
        });
    }

    @Test
    void createRejectsGarbageBody() {
        var cookie = seedSession(5_506L);
        JavalinTest.test(app(), (server, client) -> {
            var post = client.request("/api/v1/me/retainer-sales", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.post(HttpRequest.BodyPublishers.ofString("nonsense"));
            });
            assertThat(post.code()).isEqualTo(400);
        });
    }

    @Test
    void deleteMissingIdReturns404() {
        var cookie = seedSession(5_504L);
        JavalinTest.test(app(), (server, client) -> {
            var del = client.delete("/api/v1/me/retainer-sales/9999999", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(del.code()).isEqualTo(404);
        });
    }
}
