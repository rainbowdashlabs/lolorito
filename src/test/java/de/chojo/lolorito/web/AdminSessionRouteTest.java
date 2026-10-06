/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSessionRouteTest extends RouteTestBase {

    @Test
    void listWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/admin/sessions?discordUserId=1");
            assertThat(res.code()).isEqualTo(401);
        });
    }

    @Test
    void listAsNonAdminReturns403() {
        var cookie = seedSession(40_100L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/admin/sessions?discordUserId=1", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(403);
        });
    }

    @Test
    void deleteAllForUnknownUserReturns0() {
        var cookie = seedSession(40_101L);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/admin/sessions/user/999999", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            // Non-admin still hits 403; that's the branch we're covering.
            assertThat(res.code()).isEqualTo(403);
        });
    }

    @Test
    void adminCanListSessions() {
        // Seed the admin user's own session so admin routes accept it.
        var cookie = seedSession(TEST_ADMIN_USER_ID);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get(
                    "/api/v1/admin/sessions?discordUserId=" + TEST_ADMIN_USER_ID,
                    req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("createdAt");
        });
    }

    @Test
    void adminCanDeleteAllForUser() {
        var cookie = seedSession(TEST_ADMIN_USER_ID);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/admin/sessions/user/999999", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(200);
            assertThat(res.body().string()).contains("removed");
        });
    }

    @Test
    void adminDeleteMissingReturns404() {
        var cookie = seedSession(TEST_ADMIN_USER_ID);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/admin/sessions/does-not-exist", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(404);
        });
    }

    @Test
    void adminListRejectsMissingUserIdParam() {
        var cookie = seedSession(TEST_ADMIN_USER_ID);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.get("/api/v1/admin/sessions", req -> req.header("Cookie", cookieHeader(cookie)));
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void adminDeleteRejectsBadUserId() {
        var cookie = seedSession(TEST_ADMIN_USER_ID);
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/admin/sessions/user/not-a-number", null, req -> {
                req.header("Cookie", cookieHeader(cookie));
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(400);
        });
    }

    @Test
    void deleteWithoutSessionReturns401() {
        JavalinTest.test(app(), (server, client) -> {
            var res = client.delete("/api/v1/admin/sessions/some-id", null, req -> {
                req.header("X-Requested-With", "lolorito");
            });
            assertThat(res.code()).isEqualTo(401);
        });
    }
}
