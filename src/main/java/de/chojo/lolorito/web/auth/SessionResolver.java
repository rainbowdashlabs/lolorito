/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.entity.Session;
import de.chojo.lolorito.repository.Sessions;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpStatus;

import java.util.Map;

/**
 * Javalin {@code before} handler that resolves the session cookie on every
 * request, runs the auth revalidation ladder, and stashes the session on the
 * request context. Enforces:
 * <ul>
 *   <li>401 for {@code /api/v1/*} without a valid session (with a small
 *       allowlist for open endpoints like {@code /api/v1/ping}).</li>
 *   <li>CSRF gate — state-changing methods must send
 *       {@code X-Requested-With: lolorito}.</li>
 * </ul>
 */
@Singleton
public class SessionResolver {
    public static final String ATTR_SESSION = "session";
    private static final String CSRF_HEADER_NAME = "X-Requested-With";
    private static final String CSRF_HEADER_VALUE = "lolorito";

    private final File config;
    private final Sessions sessions;
    private final AuthService authService;

    @Inject
    public SessionResolver(File config, Sessions sessions, AuthService authService) {
        this.config = config;
        this.sessions = sessions;
        this.authService = authService;
    }

    public static Session sessionOf(Context ctx) {
        return ctx.attribute(ATTR_SESSION);
    }

    public void resolve(Context ctx) {
        String path = ctx.path();

        // The SPA and OAuth callbacks aren't gated at this layer.
        if (!path.startsWith("/api/v1/")) return;
        if (isOpenEndpoint(path)) {
            attachSessionIfPresent(ctx);
            return;
        }

        // CSRF gate for anything mutating.
        var method = ctx.method();
        boolean mutating = method != HandlerType.GET && method != HandlerType.HEAD && method != HandlerType.OPTIONS;
        if (mutating && !CSRF_HEADER_VALUE.equals(ctx.header(CSRF_HEADER_NAME))) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("error", "csrf"));
            ctx.skipRemainingHandlers();
            return;
        }

        Session current = loadValid(ctx);
        if (current == null) {
            clearCookie(ctx);
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            ctx.skipRemainingHandlers();
            return;
        }
        ctx.attribute(ATTR_SESSION, current);
    }

    private void attachSessionIfPresent(Context ctx) {
        Session current = loadValid(ctx);
        if (current != null) ctx.attribute(ATTR_SESSION, current);
    }

    private Session loadValid(Context ctx) {
        String cookie = ctx.cookie(config.http().sessionCookieName());
        if (cookie == null || cookie.isBlank()) return null;
        var maybe = sessions.find(cookie);
        return maybe.map(session -> authService.revalidate(session).orElse(null))
                .orElse(null);
    }

    /**
     * Endpoints the SPA (or an unauthenticated visitor via a share link) can
     * hit without a session cookie. A session is still attached when
     * present so downstream handlers can offer richer behaviour to
     * signed-in callers.
     */
    private boolean isOpenEndpoint(String path) {
        if (path.equals("/api/v1/ping")) return true;
        // Dashboard health / capacity data is operational — not tied to a
        // user session — so we let unauthenticated visitors see the "how
        // healthy is Lolorito" cards on the landing page.
        if (path.startsWith("/api/v1/calibration")) return true;
        if (path.equals("/api/v1/dashboard/stats")) return true;
        // `/baskets/shared/<token>` is the public/authenticated share read; the
        // route enforces the per-basket visibility rule downstream.
        return path.startsWith("/api/v1/baskets/shared/");
    }

    private void clearCookie(Context ctx) {
        var name = config.http().sessionCookieName();
        var flags = new StringBuilder();
        flags.append(name).append("=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
        if (config.http().secureCookies()) flags.append("; Secure");
        ctx.header("Set-Cookie", flags.toString());
    }
}
