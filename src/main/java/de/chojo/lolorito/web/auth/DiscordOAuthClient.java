/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Thin wrapper over Discord's OAuth2 endpoints and the two identity calls we
 * use ({@code /users/@me}, {@code /users/@me/guilds}). Uses the JDK HTTP
 * client so we don't pull in another dep.
 */
@Singleton
public class DiscordOAuthClient {
    private static final String BASE = "https://discord.com/api";
    private static final String AUTHORIZE = BASE + "/oauth2/authorize";
    private static final String TOKEN = BASE + "/oauth2/token";
    private static final String REVOKE = BASE + "/oauth2/token/revoke";
    private static final String ME = BASE + "/users/@me";
    private static final String ME_GUILDS = BASE + "/users/@me/guilds";
    private static final String SCOPES = "identify guilds";

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final File config;

    @Inject
    public DiscordOAuthClient(File config) {
        this.config = config;
    }

    private static String form(String... kv) {
        var sb = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append('&');
            sb.append(enc(kv[i])).append('=').append(enc(kv[i + 1]));
        }
        return sb.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public String authorizeUrl(String state) {
        return AUTHORIZE
                + "?client_id=" + enc(config.discordOauth().clientId())
                + "&redirect_uri=" + enc(redirectUri())
                + "&response_type=code"
                + "&scope=" + enc(SCOPES)
                + "&state=" + enc(state)
                + "&prompt=none";
    }

    /**
     * OAuth callback URL. In dev the frontend runs on Vite (different port
     * than the backend); Discord must redirect there so the session cookie
     * lands on the origin the SPA actually loads from — otherwise the SPA
     * on {@code :5173} never sees a cookie set on {@code :8080} and logs
     * the user straight back out.
     */
    public String redirectUri() {
        return config.http().frontendBaseUrl() + "/auth/callback";
    }

    public Token exchangeCode(String code) {
        return tokenExchange(form("grant_type", "authorization_code", "code", code, "redirect_uri", redirectUri()));
    }

    public Token refresh(String refreshToken) {
        return tokenExchange(form("grant_type", "refresh_token", "refresh_token", refreshToken));
    }

    public void revoke(String token) {
        var body = form(
                "token", token,
                "client_id", config.discordOauth().clientId(),
                "client_secret", config.discordOauth().clientSecret());
        var req = HttpRequest.newBuilder(URI.create(REVOKE))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // Best-effort. If revoke fails Discord will time the token out anyway.
        }
    }

    // -- helpers ------------------------------------------------------------

    public DiscordUser fetchUser(String accessToken) {
        var node = get(ME, accessToken);
        return new DiscordUser(
                Long.parseLong(node.get("id").asString()),
                node.get("username").asString(),
                node.hasNonNull("global_name")
                        ? node.get("global_name").asString()
                        : node.get("username").asString(),
                node.hasNonNull("avatar") ? node.get("avatar").asString() : null);
    }

    /**
     * Returns the numeric guild IDs the user is in. Discord returns strings
     * because guild IDs exceed 32-bit range; we parse to long.
     */
    public long[] fetchGuildIds(String accessToken) {
        var node = get(ME_GUILDS, accessToken);
        if (!node.isArray()) return new long[0];
        var out = new long[node.size()];
        for (int i = 0; i < node.size(); i++) {
            out[i] = Long.parseLong(node.get(i).get("id").asString());
        }
        return out;
    }

    private Token tokenExchange(String body) {
        var basic = Base64.getEncoder()
                .encodeToString((config.discordOauth().clientId() + ":"
                                + config.discordOauth().clientSecret())
                        .getBytes(StandardCharsets.UTF_8));
        var req = HttpRequest.newBuilder(URI.create(TOKEN))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + basic)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new DiscordOAuthException(
                        "Discord token endpoint returned " + res.statusCode() + ": " + res.body());
            }
            var node = json.readTree(res.body());
            long expiresIn = node.get("expires_in").asLong();
            return new Token(
                    node.get("access_token").asString(),
                    node.get("refresh_token").asString(),
                    expiresIn);
        } catch (DiscordOAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new DiscordOAuthException("Discord token exchange failed", e);
        }
    }

    private JsonNode get(String url, String accessToken) {
        var req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new DiscordOAuthException("Discord " + url + " returned " + res.statusCode() + ": " + res.body());
            }
            return json.readTree(res.body());
        } catch (DiscordOAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new DiscordOAuthException("Discord " + url + " failed", e);
        }
    }

    public record Token(String accessToken, String refreshToken, long expiresInSeconds) {}

    public record DiscordUser(long id, String username, String displayName, String avatarHash) {}

    public static class DiscordOAuthException extends RuntimeException {
        public DiscordOAuthException(String message) {
            super(message);
        }

        public DiscordOAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
