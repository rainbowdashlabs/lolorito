/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRouteTest extends RouteTestBase {

    @AfterEach
    void resetStub() {
        stubDiscord.setInGuild(true);
        stubDiscord.setThrowOnFetchUser(false);
    }

    @Test
    void loginSetsStateCookieAndRedirectsToDiscord() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/auth/login");
            assertThat(res.code()).isBetween(300, 399);
            var setCookie = res.headers().get("Set-Cookie");
            assertThat(setCookie).isNotNull();
            assertThat(String.join(",", setCookie)).contains("lolorito_oauth_state");
        });
    }

    @Test
    void callbackWithoutStateReturns400() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/auth/callback?code=abc");
            assertThat(res.code()).isEqualTo(400);
            assertThat(res.body().string()).contains("Invalid OAuth state");
        });
    }

    @Test
    void callbackWithMismatchedStateReturns400() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/auth/callback?code=abc&state=xyz", req -> req.header("Cookie", "lolorito_oauth_state=other"));
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void callbackWithMatchingStateMintsSessionAndRedirects() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/auth/callback?code=abc&state=xyz", req -> req.header("Cookie", "lolorito_oauth_state=xyz"));
            assertThat(res.code()).isBetween(300, 399);
            var setCookie = String.join(",", res.headers().get("Set-Cookie"));
            assertThat(setCookie).contains(SESSION_COOKIE_NAME + "=");
        });
    }

    @Test
    void callbackWithUserNotInGuildReturns403() {
        stubDiscord.setInGuild(false);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/auth/callback?code=abc&state=xyz", req -> req.header("Cookie", "lolorito_oauth_state=xyz"));
            assertThat(res.code()).isEqualTo(403);
            assertThat(res.body().string()).contains("member of the configured Discord");
        });
    }

    @Test
    void meReturnsCurrentDiscordUser() {
        var cookie = seedSession(2_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"username\":\"tester\"");
        });
    }

    @Test
    void meWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void meWhenDiscordFetchFailsReturns502() {
        var cookie = seedSession(2_101L);
        stubDiscord.setThrowOnFetchUser(true);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(502);
            assertThat(res.body().string()).contains("discord_unreachable");
        });
    }

    @Test
    void logoutClearsCookieAndReturns204() {
        var cookie = seedSession(2_102L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.post("/auth/logout", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(204);
        });
    }

    @Test
    void logoutWithoutSessionIsIdempotent() {
        JavalinTest.test(app(), (server, client) -> {
            // logout is under /auth/*, not gated by SessionResolver — but the resolver still runs on /api/v1/*
            // so we hit the plain /auth path without a cookie.
            var res = client.post("/auth/logout", null, req -> {});
            // Under /auth path this bypasses the CSRF/gate and just clears cookies.
            assertThat(res.code()).isEqualTo(204);
        });
    }

    @Test
    void putLocalePersistsAndReadsBack() {
        var cookie = seedSession(2_120L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put("/api/v1/me/locale", Map.of("locale", "de"), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"locale\":\"de\"");

            var me = client.get("/api/v1/me", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(me.code()).isEqualTo(200);
            assertThat(me.body().string()).contains("\"locale\":\"de\"");
        });
    }

    @Test
    void putLocaleClampsUnsupportedToEnglish() {
        var cookie = seedSession(2_121L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put("/api/v1/me/locale", Map.of("locale", "klingon"), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"locale\":\"en\"");
        });
    }

    @Test
    void putLocaleWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put(
                    "/api/v1/me/locale", Map.of("locale", "de"), req -> req.header("X-Requested-With", "lolorito"));
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void putLocaleGarbageBodyReturns400() {
        var cookie = seedSession(2_122L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.request("/api/v1/me/locale", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.put(HttpRequest.BodyPublishers.ofString("not json"));
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void putThemePersistsAndReadsBack() {
        var cookie = seedSession(2_123L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put("/api/v1/me/theme", Map.of("theme", "light"), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("\"theme\":\"light\"");
        });
    }

    @Test
    void putThemeWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put(
                    "/api/v1/me/theme", Map.of("theme", "light"), req -> req.header("X-Requested-With", "lolorito"));
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void putThemeGarbageBodyReturns400() {
        var cookie = seedSession(2_124L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.request("/api/v1/me/theme", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.put(HttpRequest.BodyPublishers.ofString("not json"));
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void plannerParamsPersistsAndReadsBack() {
        var cookie = seedSession(2_125L);
        JavalinTest.test(app(), (server, client) -> {
            var put = client.put("/api/v1/me/planner-params", Map.of("budget", 500_000, "inventorySlots", 100), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(put.code()).isEqualTo(204);
            var get = client.get("/api/v1/me/planner-params", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(get.code()).isEqualTo(200);
            String body = get.body().string().replaceAll("\\s+", "");
            assertThat(body).contains("\"budget\":500000").contains("\"inventorySlots\":100");
        });
    }

    @Test
    void plannerParamsUnauthenticatedReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/planner-params");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void plannerParamsOversizedBodyReturns400() {
        var cookie = seedSession(2_126L);
        JavalinTest.test(app(), (server, client) -> {
            String huge = "{\"x\":\"" + "a".repeat(9_000) + "\"}";
            var res = client.request("/api/v1/me/planner-params", req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
                req.header("Content-Type", "application/json");
                req.put(HttpRequest.BodyPublishers.ofString(huge));
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }
}
