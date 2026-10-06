/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.assertj.core.api.Assertions.assertThat;

class ShoppingRouteTest extends RouteTestBase {

    @BeforeAll
    static void seedRecipe() {
        query("""
                INSERT INTO worlds(region_name, data_center, data_center_name, world, world_name)
                VALUES ('Europe', 7, 'Light', 66, 'Odin')
                ON CONFLICT (data_center, world) DO NOTHING
                """).single(call()).insert();
        query(
                        "INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty) VALUES (9001, 32001, 'BSM', 10, 1)")
                .single(call())
                .insert();
        query("INSERT INTO recipe_ingredient (recipe_id, item_id, quantity) VALUES (9001, 32002, 2)")
                .single(call())
                .insert();
        query("""
                INSERT INTO listings(world, item, hq, review_time, unit_price, quantity, total)
                VALUES (66, 32002, false, now(), 40, 99, 3960)
                """).single(call()).insert();
    }

    private static String post(io.javalin.testtools.HttpClient client, String cookie, String json) throws Exception {
        var res = client.post("/api/v1/shopping", json, req -> {
            req.header("Cookie", cookieHeader(cookie));
            req.header("X-Requested-With", "lolorito");
        });
        return res.code() + " " + res.body().string();
    }

    @Test
    void plansAShoppingRunForACraftableItem() {
        var cookie = seedSession(1_420L);
        JavalinTest.test(app(), (server, client) -> {
            var out = post(
                    client,
                    cookie,
                    "{\"itemId\":32001,\"count\":3,\"homeWorld\":66,\"overrides\":{\"32002\":\"buy\"}}");
            assertThat(out).startsWith("200 ");
            assertThat(out)
                    .contains("\"recipeId\":9001")
                    .contains("\"totalCost\":240")
                    .contains("\"recipes\":[");
        });
    }

    @Test
    void rejectsInvalidRequests() {
        var cookie = seedSession(1_421L);
        JavalinTest.test(app(), (server, client) -> {
            assertThat(post(client, cookie, "not json")).startsWith("400 ");
            assertThat(post(client, cookie, "{\"homeWorld\":66}"))
                    .startsWith("400 ")
                    .contains("itemId required");
            assertThat(post(client, cookie, "{\"itemId\":32001}"))
                    .startsWith("400 ")
                    .contains("homeWorld not set");
            assertThat(post(client, cookie, "{\"itemId\":32001,\"homeWorld\":66,\"overrides\":{\"32002\":\"steal\"}}"))
                    .startsWith("400 ")
                    .contains("BUY or CRAFT");
        });
    }

    @Test
    void unknownProductsAreNotFound() {
        var cookie = seedSession(1_422L);
        JavalinTest.test(app(), (server, client) -> {
            assertThat(post(client, cookie, "{\"itemId\":32999,\"homeWorld\":66}"))
                    .startsWith("404 ");
        });
    }
}
