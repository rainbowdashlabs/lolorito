/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.assertj.core.api.Assertions.assertThat;

class TrendRouteTest extends RouteTestBase {

    private static void seedRisingSales(int itemId) {
        for (int day = 0; day < 7; day++) {
            query("""
                    INSERT INTO sales(world, item, hq, sold, unit_price, quantity, total)
                    VALUES (66, :i, false, now() - ((:d || ' days')::INTERVAL) - INTERVAL '1 hour', 100, :q, :t)
                    """)
                    .single(call().bind("i", itemId)
                            .bind("d", String.valueOf(day))
                            .bind("q", 7 - day)
                            .bind("t", 100 * (7 - day)))
                    .insert();
        }
    }

    @Test
    void boardReturnsTrendingKeysForTheHomeWorld() {
        seedRisingSales(31_001);
        var cookie = seedSession(1_400L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/trends?home_world=66&window=7", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            var body = res.body().string();
            assertThat(body)
                    .contains("\"homeWorldId\":66")
                    .contains("\"itemId\":31001")
                    .contains("\"losing\":[");
        });
    }

    @Test
    void boardNeedsAKnownHomeWorld() {
        var cookie = seedSession(1_401L);
        JavalinTest.test(app(), (server, client) -> {
            assertThat(client.get("/api/v1/trends", req -> req.header("Cookie", cookieHeader(cookie)))
                            .code())
                    .isEqualTo(400);
            assertThat(client.get("/api/v1/trends?home_world=9999", req -> req.header("Cookie", cookieHeader(cookie)))
                            .code())
                    .isEqualTo(400);
        });
    }

    @Test
    void itemTrendReturnsTheFitOrNoContent() {
        seedRisingSales(31_002);
        var cookie = seedSession(1_402L);
        JavalinTest.test(app(), (server, client) -> {
            var fitted = client.get(
                    "/api/v1/items/31002/trend?home_world=66", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(fitted.code()).isEqualTo(200);
            assertThat(fitted.body().string()).contains("\"itemId\":31002").contains("\"predictedNext24h\"");

            var quiet = client.get(
                    "/api/v1/items/31999/trend?home_world=66", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(quiet.code()).isEqualTo(204);
        });
    }

    @Test
    void itemTrendRejectsBadInput() {
        var cookie = seedSession(1_403L);
        JavalinTest.test(app(), (server, client) -> {
            assertThat(client.get(
                                    "/api/v1/items/abc/trend?home_world=66",
                                    req -> req.header("Cookie", cookieHeader(cookie)))
                            .code())
                    .isEqualTo(400);
            assertThat(client.get("/api/v1/items/100/trend", req -> req.header("Cookie", cookieHeader(cookie)))
                            .code())
                    .isEqualTo(400);
            assertThat(client.get(
                                    "/api/v1/items/100/trend?home_world=9999",
                                    req -> req.header("Cookie", cookieHeader(cookie)))
                            .code())
                    .isEqualTo(400);
        });
    }
}
