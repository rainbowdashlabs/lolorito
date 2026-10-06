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
import de.chojo.lolorito.service.UserPreferencesService;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.auth.DiscordOAuthClient.DiscordUser;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.HttpStatus;
import io.javalin.http.SameSite;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

@Singleton
public class AuthRoutes implements Routes {
    private static final Logger log = getLogger(AuthRoutes.class);
    private static final String STATE_COOKIE = "lolorito_oauth_state";

    private final File config;
    private final AuthService authService;
    private final DiscordOAuthClient discord;
    private final TokenCipher cipher;
    private final UserPreferencesService preferences;
    private final AuthRateLimiter rateLimiter;

    @Inject
    public AuthRoutes(
            File config,
            AuthService authService,
            DiscordOAuthClient discord,
            TokenCipher cipher,
            UserPreferencesService preferences,
            AuthRateLimiter rateLimiter) {
        this.config = config;
        this.authService = authService;
        this.discord = discord;
        this.cipher = cipher;
        this.preferences = preferences;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/auth/login", this::login);
        routes.get("/auth/callback", this::callback);
        routes.post("/auth/logout", this::logout);
        routes.get("/api/v1/me", this::me);
        routes.put("/api/v1/me/locale", this::putLocale);
        routes.put("/api/v1/me/theme", this::putTheme);
        routes.get("/api/v1/me/planner-params", this::getPlannerParams);
        routes.put("/api/v1/me/planner-params", this::putPlannerParams);
        routes.get("/api/v1/me/alert-webhook", this::getAlertWebhook);
        routes.put("/api/v1/me/alert-webhook", this::putAlertWebhook);
    }

    private void getAlertWebhook(Context ctx) {
        Session session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        String url = preferences.alertWebhookUrlFor(session.discordUserId());
        ctx.json(Map.of("url", url == null ? "" : url));
    }

    private void putAlertWebhook(Context ctx) {
        Session session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        String url = body.get("url") == null ? null : String.valueOf(body.get("url"));
        // Only accept http/https so a stray "javascript:" or "file:" doesn't
        // slip through into the outbound HTTP client.
        if (url != null && !url.isBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "url must be http(s)://"));
            return;
        }
        var prefs = preferences.setAlertWebhookUrl(session.discordUserId(), url);
        String stored = prefs.alertWebhookUrl();
        ctx.json(Map.of("url", stored == null ? "" : stored));
    }

    private void getPlannerParams(Context ctx) {
        Session session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        String json = preferences.plannerParams(session.discordUserId());
        // Empty body → the SPA falls back to its built-in defaults.
        ctx.contentType("application/json").result(json == null ? "null" : json);
    }

    private void putPlannerParams(Context ctx) {
        Session session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        String raw = ctx.body();
        // Sanity: reject anything larger than 8 KB — the form is a couple
        // dozen scalars, an oversized body is an integration bug.
        if (raw == null || raw.length() > 8192) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        preferences.setPlannerParams(session.discordUserId(), raw);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void putLocale(Context ctx) {
        Session session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        var locale = String.valueOf(body.getOrDefault("locale", "en"));
        var prefs = preferences.setLocale(session.discordUserId(), locale);
        ctx.json(Map.of("locale", prefs.locale(), "theme", prefs.theme() == null ? "" : prefs.theme()));
    }

    private void putTheme(Context ctx) {
        Session session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        var theme = body.get("theme") == null ? null : String.valueOf(body.get("theme"));
        var prefs = preferences.setTheme(session.discordUserId(), theme);
        ctx.json(Map.of("locale", prefs.locale(), "theme", prefs.theme() == null ? "" : prefs.theme()));
    }

    private void login(Context ctx) {
        if (!rateLimiter.tryAcquire(clientKey(ctx))) {
            log.warn("Auth /login rate limited for {}", clientKey(ctx));
            ctx.status(HttpStatus.TOO_MANY_REQUESTS).result("Too many login attempts. Slow down.");
            return;
        }
        var state = authService.newOauthState();
        setStateCookie(ctx, state, Duration.ofMinutes(10));
        ctx.redirect(authService.authorizeUrl(state));
    }

    private void callback(Context ctx) {
        if (!rateLimiter.tryAcquire(clientKey(ctx))) {
            log.warn("Auth /callback rate limited for {}", clientKey(ctx));
            ctx.status(HttpStatus.TOO_MANY_REQUESTS).result("Too many auth attempts.");
            return;
        }
        String code = ctx.queryParam("code");
        String state = ctx.queryParam("state");
        String stateCookie = ctx.cookie(STATE_COOKIE);
        clearStateCookie(ctx);

        if (code == null || state == null || !state.equals(stateCookie)) {
            log.info("Auth callback rejected: state mismatch or missing code");
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid OAuth state. Try logging in again.");
            return;
        }

        try {
            String sessionId = authService.completeLogin(code, ctx.userAgent());
            setSessionCookie(ctx, sessionId);
            // Send the browser back to the frontend origin — in dev that's
            // the Vite dev server, in prod the same origin as the backend.
            ctx.redirect(config.http().frontendBaseUrl() + "/");
        } catch (AuthService.AuthException e) {
            ctx.status(HttpStatus.FORBIDDEN).result("You must be a member of the configured Discord to log in.");
        } catch (Exception e) {
            log.warn("Auth callback failed", e);
            ctx.status(HttpStatus.BAD_GATEWAY).result("Discord login failed. Try again.");
        }
    }

    private void logout(Context ctx) {
        var session = SessionResolver.sessionOf(ctx);
        if (session != null) authService.logout(session);
        clearSessionCookie(ctx);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void me(Context ctx) {
        Session session = SessionResolver.sessionOf(ctx);
        if (session == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            return;
        }
        DiscordUser user;
        try {
            var access = cipher.decrypt(session.accessTokenCiphertext(), session.accessTokenIv());
            user = discord.fetchUser(access);
        } catch (Exception e) {
            log.warn("Failed to fetch /users/@me for session {}", session.id(), e);
            ctx.status(HttpStatus.BAD_GATEWAY).json(Map.of("error", "discord_unreachable"));
            return;
        }

        var body = new HashMap<String, Object>();
        body.put("id", String.valueOf(user.id()));
        body.put("username", user.username());
        body.put("displayName", user.displayName());
        body.put(
                "avatarUrl",
                user.avatarHash() == null
                        ? null
                        : "https://cdn.discordapp.com/avatars/" + user.id() + "/" + user.avatarHash()
                                + ".png?size=128");
        var prefs = preferences.current(session.discordUserId());
        body.put("locale", prefs.locale());
        body.put("theme", prefs.theme());
        ctx.json(body);
    }

    // -- cookie helpers ------------------------------------------------------

    /** Pick a reasonable per-caller key — trust {@code X-Forwarded-For} when set, otherwise remote IP. */
    private static String clientKey(Context ctx) {
        String xff = ctx.header("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return ctx.ip();
    }

    private void setStateCookie(Context ctx, String value, Duration ttl) {
        ctx.cookie(buildCookie(STATE_COOKIE, value, (int) ttl.toSeconds()));
    }

    private void clearStateCookie(Context ctx) {
        ctx.cookie(buildCookie(STATE_COOKIE, "", 0));
    }

    private void setSessionCookie(Context ctx, String value) {
        var maxAge = (int) Duration.ofDays(config.http().sessionMaxAgeDays()).toSeconds();
        ctx.cookie(buildCookie(config.http().sessionCookieName(), value, maxAge));
    }

    private void clearSessionCookie(Context ctx) {
        ctx.cookie(buildCookie(config.http().sessionCookieName(), "", 0));
    }

    private Cookie buildCookie(String name, String value, int maxAgeSeconds) {
        var cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setHttpOnly(true);
        cookie.setSecure(config.http().secureCookies());
        cookie.setSameSite(SameSite.LAX);
        return cookie;
    }
}
