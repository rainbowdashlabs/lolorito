/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import de.chojo.lolorito.entity.OfferFilterRow;
import de.chojo.lolorito.repository.OfferFilters;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.assertj.core.api.Assertions.assertThat;

class OffersRouteTest extends RouteTestBase {

    @Test
    void withoutHomeWorldReturns400() {
        var cookie = seedSession(1_300L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/offers", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(400);
            assertThat(res.body().string()).contains("home_world");
        });
    }

    @Test
    void withUnknownHomeWorldReturns400() {
        var cookie = seedSession(1_301L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/offers?home_world=" + Integer.MAX_VALUE,
                    req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(400);
            assertThat(res.body().string()).contains("unknown home_world");
        });
    }

    @Test
    void withHomeWorldReturnsEmptyOffersEnvelope() {
        var cookie = seedSession(1_302L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/offers?home_world=66", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            var body = res.body().string();
            assertThat(body).contains("homeWorld");
            assertThat(body).contains("Odin");
            assertThat(body).contains("count");
        });
    }

    @Test
    void fallsBackToSavedFilterHomeWorld() {
        var userId = 1_303L;
        var cookie = seedSession(userId);
        // Seed a saved filter with worldId=66 so we don't need ?home_world=.
        var filters = injector.getInstance(OfferFilters.class);
        var row = new OfferFilterRow(66, 100, 100, 1.2, 24, 0, 0, 0, 0, 0, 100, 100, "DATA_CENTER");
        filters.upsert(userId, row);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/offers", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("Odin");
        });
        query("DELETE FROM offer_filter WHERE user_id = :u")
                .single(call().bind("u", userId))
                .delete();
    }
}
