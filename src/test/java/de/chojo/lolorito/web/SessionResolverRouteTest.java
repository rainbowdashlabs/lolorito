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

class SessionResolverRouteTest extends RouteTestBase {

    @Test
    void protectedRouteWithoutCookieIs401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/filter");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void protectedRouteWithUnknownCookieIs401AndClearsCookie() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/filter", req -> req.header("Cookie", cookieHeader("nope")));
            assertThat(res.code()).isEqualTo(401);
            var setCookie = res.headers().get("Set-Cookie");
            assertThat(setCookie).isNotNull();
            assertThat(String.join(",", setCookie)).contains(SESSION_COOKIE_NAME + "=");
        });
    }

    @Test
    void mutatingRouteRejectsMissingCsrfHeader() {
        String cookie = seedSession(9001L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put("/api/v1/me/filter", Map.of(), req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(403);
            assertThat(res.body().string()).contains("csrf");
        });
    }

    @Test
    void mutatingRouteSucceedsWithCsrfHeader() {
        String cookie = seedSession(9002L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.put("/api/v1/me/filter", Map.of(), req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
        });
    }

    @Test
    void authenticatedGetRouteReadsFilterRow() {
        String cookie = seedSession(9003L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/me/filter", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("worldId");
        });
    }

    @Test
    void openEndpointIgnoresBadCookie() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/ping", req -> req.header("Cookie", cookieHeader("junk")));
            assertThat(res.code()).isEqualTo(200);
        });
    }

    @Test
    void nonApiPathBypassesResolver() {
        // /auth/* is not gated by the SessionResolver — login should proceed to Discord.
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/auth/login");
            // login is a 302 redirect to the stub authorize URL; either way not a 401.
            assertThat(res.code()).isNotEqualTo(401);
        });
    }
}
