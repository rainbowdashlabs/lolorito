/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import de.chojo.lolorito.service.BasketService;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.assertj.core.api.Assertions.assertThat;

class BasketRouteTest extends RouteTestBase {

    @Test
    void createRoundTripsAndListShowsBasket() {
        var cookie = seedSession(4_100L);
        JavalinTest.test(app(), (server, client) -> {
            var item = Map.ofEntries(
                    Map.entry("key", "k1"),
                    Map.entry("itemId", 100),
                    Map.entry("itemName", "Bronze"),
                    Map.entry("hq", false),
                    Map.entry("sourceWorldId", 66),
                    Map.entry("sourceWorldName", "Odin"),
                    Map.entry("quantity", 5),
                    Map.entry("buyPrice", 400),
                    Map.entry("action", "resale"),
                    Map.entry("evPerHour", 1_234.5),
                    Map.entry("addedAt", "2026-07-02T10:00:00Z"));
            var res = client.post(
                    "/api/v1/baskets", Map.of("name", "trip", "visibility", "public", "items", List.of(item)), req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            assertThat(res.code()).isEqualTo(201);
            var body = res.body().string();
            assertThat(body).contains("shareToken");
            assertThat(body).contains("\"visibility\":\"public\"");
            assertThat(body).contains("Bronze");

            var list = client.get("/api/v1/baskets", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(list.code()).isEqualTo(200);
            assertThat(list.body().string()).contains("trip").contains("Bronze");
        });
    }

    @Test
    void ownerCanReadUpdateAndOverwriteItems() {
        var cookie = seedSession(4_120L);
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post(
                    "/api/v1/baskets", Map.of("name", "before", "visibility", "private", "items", List.of()), req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            String id = extractId(created.body().string());

            var readOwn = client.get("/api/v1/baskets/" + id, req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(readOwn.code()).isEqualTo(200);
            assertThat(readOwn.body().string()).contains("before");

            var item = Map.ofEntries(
                    Map.entry("key", "kk"),
                    Map.entry("itemId", 42),
                    Map.entry("itemName", "Iron"),
                    Map.entry("hq", true),
                    Map.entry("sourceWorldId", 66),
                    Map.entry("sourceWorldName", "Odin"),
                    Map.entry("quantity", 1),
                    Map.entry("buyPrice", 100),
                    Map.entry("action", "resale"),
                    Map.entry("evPerHour", 10.0),
                    Map.entry("addedAt", "2026-07-02T10:00:00Z"));
            var updated = client.put(
                    "/api/v1/baskets/" + id,
                    Map.of("name", "after", "visibility", "authenticated", "items", List.of(item)),
                    req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            assertThat(updated.code()).isEqualTo(200);
            var updatedBody = updated.body().string();
            assertThat(updatedBody).contains("after");
            assertThat(updatedBody).contains("Iron");
            assertThat(updatedBody).contains("authenticated");
        });
    }

    @Test
    void readOwnMissingReturns404() {
        var cookie = seedSession(4_121L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/baskets/" + UUID.randomUUID(), req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(404);
        });
    }

    @Test
    void invalidUuidOnDeleteReturns400() {
        var cookie = seedSession(4_122L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/baskets/not-a-uuid", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void garbageBodyOnUpdateReturns400() {
        var cookie = seedSession(4_123L);
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post(
                    "/api/v1/baskets", Map.of("name", "b", "visibility", "private", "items", List.of()), req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            String id = extractId(created.body().string());
            var res = client.request("/api/v1/baskets/" + id, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.put(HttpRequest.BodyPublishers.ofString("not json"));
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void publicShareVisibleWithoutSession() {
        var cookie = seedSession(4_101L);
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post(
                    "/api/v1/baskets",
                    Map.of("name", "public-basket", "visibility", "public", "items", List.of()),
                    req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            String token = extractShareToken(created.body().string());

            var res = client.get("/api/v1/baskets/shared/" + token);
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"visibility\":\"public\"");
        });
    }

    @Test
    void authenticatedShareRejectsAnonymousAndAcceptsSession() {
        var owner = seedSession(4_102L);
        var visitor = seedSession(4_103L);
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post(
                    "/api/v1/baskets",
                    Map.of("name", "signed-in-only", "visibility", "authenticated", "items", List.of()),
                    req -> {
                        req.header("Cookie", cookieHeader(owner));
                        req.header("X-Requested-With", "lolorito");
                    });
            String token = extractShareToken(created.body().string());

            var anon = client.get("/api/v1/baskets/shared/" + token);
            assertThat(anon.code()).isEqualTo(404);

            var signedIn =
                    client.get("/api/v1/baskets/shared/" + token, req -> req.header("Cookie", cookieHeader(visitor)));
            assertThat(signedIn.code()).isEqualTo(200);
        });
    }

    @Test
    void privateSharedLinkIs404EvenWithSession() {
        var cookie = seedSession(4_104L);
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post(
                    "/api/v1/baskets", Map.of("name", "mine", "visibility", "private", "items", List.of()), req -> {
                        req.header("Cookie", cookieHeader(cookie));
                        req.header("X-Requested-With", "lolorito");
                    });
            String token = extractShareToken(created.body().string());

            var res = client.get("/api/v1/baskets/shared/" + token, req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(404);
        });
    }

    @Test
    void updateAndDeleteHonourOwnership() {
        var owner = seedSession(4_105L);
        var intruder = seedSession(4_106L);
        JavalinTest.test(app(), (server, client) -> {
            var created = client.post(
                    "/api/v1/baskets", Map.of("name", "keep", "visibility", "private", "items", List.of()), req -> {
                        req.header("Cookie", cookieHeader(owner));
                        req.header("X-Requested-With", "lolorito");
                    });
            String id = extractId(created.body().string());

            var intruderPut = client.put("/api/v1/baskets/" + id, Map.of("name", "hijacked"), req -> {
                req.header("Cookie", cookieHeader(intruder));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(intruderPut.code()).isEqualTo(404);

            var intruderDel = client.delete("/api/v1/baskets/" + id, null, req -> {
                req.header("Cookie", cookieHeader(intruder));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(intruderDel.code()).isEqualTo(404);

            var ownerDel = client.delete("/api/v1/baskets/" + id, null, req -> {
                req.header("Cookie", cookieHeader(owner));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(ownerDel.code()).isEqualTo(204);
        });
    }

    @Test
    void invalidUuidReturns400() {
        var cookie = seedSession(4_107L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/baskets/not-a-uuid", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void garbageBodyOnCreateReturns400() {
        var cookie = seedSession(4_108L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.request("/api/v1/baskets", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.post(HttpRequest.BodyPublishers.ofString("not json"));
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void serviceEnforcesOwnershipDirectly() {
        // Extra assertion around findOwn — visitor sees empty even though the row exists.
        var svc = injector.getInstance(BasketService.class);
        long owner = 4_200L;
        long other = 4_201L;
        query("INSERT INTO offer_filter (user_id) VALUES (:u) ON CONFLICT DO NOTHING")
                .single(call().bind("u", owner))
                .insert();
        var b = svc.create(owner, "private", null, List.of());
        assertThat(svc.findOwn(b.id(), owner)).isPresent();
        assertThat(svc.findOwn(b.id(), other)).isEmpty();
    }

    // -- Helpers -----------------------------------------------------------

    private static String extractShareToken(String body) {
        return between(body, "\"shareToken\":\"", "\"");
    }

    private static String extractId(String body) {
        return between(body, "\"id\":\"", "\"");
    }

    private static String between(String s, String lead, String tail) {
        int i = s.indexOf(lead);
        if (i < 0) throw new AssertionError("missing key: " + lead + " in " + s);
        int start = i + lead.length();
        int end = s.indexOf(tail, start);
        return s.substring(start, end);
    }
}
