/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.web.auth.AuthService;
import de.chojo.lolorito.web.auth.DiscordOAuthClient;
import de.chojo.lolorito.web.auth.SessionResolver;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Javalin bootstrap. Consumes the full set of {@link Routes} via Guice
 * multibinding — new endpoints slot in by adding a binding in
 * {@link de.chojo.lolorito.LoloritoModule}, no wiring changes here.
 *
 * <p>Ships two ember-style middleware bands:
 * <ol>
 *   <li><em>Trace request/response</em> — both directions, with header +
 *       body preview + query redaction via {@link LogRedaction}. Off at
 *       INFO, on at TRACE.</li>
 *   <li><em>Structured exception → JSON</em> — unknown errors log at
 *       {@code error}; 4xxs log at {@code warn}; every failing request
 *       gets an {@link ErrorResponse} JSON body instead of Javalin's
 *       default HTML page.</li>
 * </ol>
 */
@Singleton
public class Web {
    private static final Logger log = getLogger(Web.class);
    private static final String JSON = "application/json";
    /** Attribute name used to thread request-start timestamps through the middleware. */
    private static final String REQ_START_ATTR = "_requestStart";
    /** Body preview caps — enough to see the shape without dumping full payloads. */
    private static final int REQ_BODY_PREVIEW = 180;

    private static final int RES_BODY_PREVIEW = 360;

    private final File config;
    private final Set<Routes> routes;
    private final SessionResolver sessionResolver;
    private final AuthService authService;
    private final ValuationBroadcaster broadcaster;
    private Javalin app;

    @Inject
    public Web(
            File config,
            Set<Routes> routes,
            SessionResolver sessionResolver,
            AuthService authService,
            ValuationBroadcaster broadcaster) {
        this.config = config;
        this.routes = routes;
        this.sessionResolver = sessionResolver;
        this.authService = authService;
        this.broadcaster = broadcaster;
    }

    public void start() {
        app = build();
        var http = config.http();
        app.start(http.host(), http.port());
        log.info("Web listening on {}:{}", http.host(), http.port());
    }

    public Javalin build() {
        return Javalin.create(cfg -> {
            cfg.http.defaultContentType = JSON;

            // Jackson 3 — ISO-8601 for Instants by default, unlike Javalin's
            // bundled Jackson 2 adapter which emits numeric epoch seconds
            // and breaks any client that expects a string timestamp
            // (dashboard chart's time axis, DateTime formatting, etc.).
            cfg.jsonMapper(new Jackson3Mapper(JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build()));

            cfg.staticFiles.add(staticCfg -> {
                staticCfg.directory = "/web";
                staticCfg.location = Location.CLASSPATH;
            });
            cfg.spaRoot.addFile("/", "/web/index.html", Location.CLASSPATH);

            installTraceLogging(cfg.routes);
            installRequestTiming(cfg.routes);

            cfg.routes.before(sessionResolver::resolve);

            cfg.routes.get(
                    "/api/v1/ping",
                    ctx -> ctx.json(
                            Map.of("ok", true, "serverTime", Instant.now().toString())));

            for (Routes r : routes) r.register(cfg.routes);

            // WebSocket push for market-model refits — SPA subscribes and
            // invalidates its own caches when a material valuation changes.
            cfg.routes.ws("/api/v1/ws/valuations", ws -> {
                ws.onConnect(broadcaster::addSession);
                ws.onClose(broadcaster::removeSession);
                ws.onError(broadcaster::removeSession);
            });

            installExceptionHandlers(cfg.routes);
        });
    }

    // ---------------------------------------------------------------- middleware

    /** TRACE-only inbound + outbound dump. Structured to survive redaction. */
    private static void installTraceLogging(JavalinDefaultRoutingApi routes) {
        routes.before(ctx -> {
            if (!log.isTraceEnabled()) return;
            if (ctx.method() == HandlerType.OPTIONS) return;
            String bodyLog = requestBodyPreview(ctx);
            log.trace(
                    "Received request on route: {} {}\nHeaders:\n{}\nBody:\n{}",
                    ctx.method() + " " + LogRedaction.redactQueryString(ctx.url()),
                    LogRedaction.redactQueryString(Objects.requireNonNullElse(ctx.queryString(), "")),
                    LogRedaction.redactHeaders(ctx.headerMap()).entrySet().stream()
                            .map(h -> "   " + h.getKey() + ": " + h.getValue())
                            .collect(Collectors.joining("\n")),
                    bodyLog);
        });

        routes.after(ctx -> {
            if (!log.isTraceEnabled()) return;
            if (ctx.method() == HandlerType.OPTIONS) return;
            String responseBody = responseBodyPreview(ctx);
            var responseHeaders = new LinkedHashMap<String, String>();
            for (String h : ctx.res().getHeaderNames()) {
                responseHeaders.put(h, ctx.res().getHeader(h));
            }
            log.trace(
                    "Answered request on route: {} {}\nStatus: {}\nHeaders:\n{}\nBody:\n{}",
                    ctx.method() + " " + LogRedaction.redactQueryString(ctx.url()),
                    LogRedaction.redactQueryString(Objects.requireNonNullElse(ctx.queryString(), "")),
                    ctx.status(),
                    LogRedaction.redactHeaders(responseHeaders).entrySet().stream()
                            .map(h -> "   " + h.getKey() + ": " + h.getValue())
                            .collect(Collectors.joining("\n")),
                    responseBody);
        });
    }

    /** Slow-request warning — /api/* only, DEBUG for a normal response, WARN when we take {@literal >} 1 s. */
    private static void installRequestTiming(JavalinDefaultRoutingApi routes) {
        routes.before(ctx -> ctx.attribute(REQ_START_ATTR, System.currentTimeMillis()));
        routes.after(ctx -> {
            Long start = ctx.attribute(REQ_START_ATTR);
            if (start == null) return;
            if (!ctx.path().startsWith("/api/")) return;
            long duration = System.currentTimeMillis() - start;
            if (duration >= 1000) {
                log.warn(
                        "Slow request: {} {} took {} ms (status {})",
                        ctx.method(),
                        LogRedaction.redactQueryString(ctx.path()),
                        duration,
                        ctx.statusCode());
            } else if (log.isDebugEnabled()) {
                log.debug(
                        "{} {} → {} in {} ms",
                        ctx.method(),
                        LogRedaction.redactQueryString(ctx.path()),
                        ctx.statusCode(),
                        duration);
            }
        });
    }

    /** ember-style structured JSON error response + per-severity logging. Package-private for tests. */
    static void installExceptionHandlers(JavalinDefaultRoutingApi routes) {
        routes.exception(AuthService.AuthException.class, (err, ctx) -> {
            log.warn("Auth failure on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.json(new ErrorResponse("Unauthorized", err.getMessage()));
        });

        routes.exception(DiscordOAuthClient.DiscordOAuthException.class, (err, ctx) -> {
            log.warn("Discord OAuth failure on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
            ctx.status(HttpStatus.BAD_GATEWAY);
            ctx.json(new ErrorResponse("Bad Gateway", err.getMessage()));
        });

        routes.exception(HttpResponseException.class, (err, ctx) -> {
            int code = err.getStatus();
            if (code >= 500) {
                log.error("HTTP {} on {} {}: {}", code, ctx.method(), ctx.path(), err.getMessage(), err);
            } else if (code == 404) {
                log.warn("HTTP 404 on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
            } else if (code >= 400 && code != 401) {
                log.warn("HTTP {} on {} {}: {}", code, ctx.method(), ctx.path(), err.getMessage());
            }
            var status = HttpStatus.forStatus(code);
            ctx.status(status);
            ctx.json(new ErrorResponse(status.getMessage(), err.getMessage()));
        });

        routes.exception(IllegalArgumentException.class, (err, ctx) -> {
            log.warn("Invalid input on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new ErrorResponse("Invalid Input", err.getMessage()));
        });

        routes.exception(Exception.class, (err, ctx) -> {
            log.error("Unhandled exception on {} {}", ctx.method(), ctx.path(), err);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new ErrorResponse("Internal Server Error"));
        });
    }

    // ---------------------------------------------------------------- body previews

    private static String requestBodyPreview(Context ctx) {
        String path = ctx.path();
        // OAuth flow bodies carry authorization codes / tokens.
        if (path.startsWith("/auth/") || path.startsWith("/api/v1/me")) return LogRedaction.SENTINEL;
        String contentType = ctx.contentType();
        if (contentType == null || contentType.contains("text") || contentType.startsWith(JSON)) {
            String body = ctx.body();
            if (body.isEmpty()) return "";
            return body.substring(0, Math.min(body.length(), REQ_BODY_PREVIEW));
        }
        return "Bytes";
    }

    private static String responseBodyPreview(Context ctx) {
        String path = ctx.path();
        if (path.startsWith("/auth/") || path.startsWith("/api/v1/me")) return LogRedaction.SENTINEL;
        String contentType = ctx.res().getContentType();
        if (contentType != null && contentType.startsWith(JSON)) {
            String result = Objects.requireNonNullElse(ctx.result(), "");
            return result.substring(0, Math.min(result.length(), RES_BODY_PREVIEW));
        }
        return "Bytes";
    }

    public AuthService authService() {
        return authService;
    }

    public void stop() {
        if (app != null) app.stop();
    }
}
