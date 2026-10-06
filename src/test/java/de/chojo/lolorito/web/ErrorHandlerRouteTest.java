/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import de.chojo.lolorito.web.auth.AuthService;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the ported ember-style exception handlers directly by building
 * a standalone Javalin that installs them plus a set of test-only routes
 * that trip every branch.
 */
class ErrorHandlerRouteTest {

    private static Javalin build() {
        return Javalin.create(cfg -> {
            cfg.routes.get("/test/unhandled", ctx -> {
                throw new IllegalStateException("boom");
            });
            cfg.routes.get("/test/bad-input", ctx -> {
                throw new IllegalArgumentException("nope");
            });
            cfg.routes.get("/test/http", ctx -> {
                throw new NotFoundResponse("missing");
            });
            cfg.routes.get("/test/auth", ctx -> {
                throw new AuthService.AuthException("not you");
            });
            Web.installExceptionHandlers(cfg.routes);
        });
    }

    @Test
    void illegalArgumentBecomes400WithJsonEnvelope() {
        JavalinTest.test(build(), (server, client) -> {
            var res = client.get("/test/bad-input");
            assertThat(res.code()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
            String body = res.body().string();
            assertThat(body).contains("Invalid Input").contains("nope");
        });
    }

    @Test
    void httpResponseKeepsItsStatus() {
        JavalinTest.test(build(), (server, client) -> {
            var res = client.get("/test/http");
            assertThat(res.code()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
            assertThat(res.body().string()).contains("missing");
        });
    }

    @Test
    void unhandledExceptionBecomes500WithoutLeakingMessage() {
        JavalinTest.test(build(), (server, client) -> {
            var res = client.get("/test/unhandled");
            assertThat(res.code()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
            String body = res.body().string();
            assertThat(body).contains("Internal Server Error").doesNotContain("boom");
        });
    }

    @Test
    void authExceptionBecomes401() {
        JavalinTest.test(build(), (server, client) -> {
            var res = client.get("/test/auth");
            assertThat(res.code()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
            assertThat(res.body().string()).contains("Unauthorized").contains("not you");
        });
    }
}
