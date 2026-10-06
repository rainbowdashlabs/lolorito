/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.config.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
@OverwritePrefix("HTTP")
public class Http {
    @Overwrite(env = @Env)
    private int port = 8080;

    @Overwrite(env = @Env)
    private String host = "0.0.0.0";

    /**
     * Public origin the SPA/redirects are served from — e.g.
     * {@code https://lolorito.example}. Used to construct the OAuth redirect URI
     * and for absolute redirects. In local dev this is {@code http://localhost:8080}.
     */
    @Overwrite(env = @Env)
    private String publicBaseUrl = "http://localhost:8080";

    /**
     * Where the browser actually loads the SPA from. In prod this equals
     * {@link #publicBaseUrl}; in dev it's the Vite dev server (typically
     * {@code http://localhost:5173}) which proxies {@code /api/*} and
     * {@code /auth/*} back to this backend. Discord OAuth callbacks land
     * here and the post-login redirect points here too — otherwise the
     * session cookie ends up on the wrong origin and the SPA never sees
     * it.
     *
     * <p>When left blank we fall back to {@link #publicBaseUrl}, so a
     * single-origin prod deploy needs no extra config.
     */
    @Overwrite(env = @Env)
    private String frontendBaseUrl = "";

    @Overwrite(env = @Env)
    private String sessionCookieName = "lolorito_session";

    /**
     * Rolling max age for a session cookie. Slides forward on every request
     * (see {@link de.chojo.lolorito.web.auth.AuthService#revalidate}), so an
     * active user stays signed in indefinitely and only gets bounced back to
     * Discord OAuth after {@code sessionMaxAgeDays} of complete inactivity.
     */
    @Overwrite(env = @Env)
    private int sessionMaxAgeDays = 90;

    /**
     * Base64-encoded 32-byte AES key. Empty until an operator provides one; the
     * app refuses to boot with a blank value.
     */
    @Overwrite(env = @Env)
    private String tokenEncryptionKey = "";

    /**
     * How often {@link de.chojo.lolorito.web.auth.AuthService#revalidate}
     * re-checks guild membership. The bot's
     * {@code GuildMemberRemoveEvent} listener already tears down sessions
     * live when a user leaves, so this TTL is a belt-and-braces defense
     * against a missed gateway event. A longer window means fewer chances
     * for a transient cache miss to look like a departure.
     */
    @Overwrite(env = @Env)
    private int guildRecheckTtlMinutes = 60;

    @Overwrite(env = @Env)
    private int guildRecheckTtlBackgroundMinutes = 60;

    @Overwrite(env = @Env)
    private int sweeperIntervalMinutes = 15;

    /**
     * Emit {@code Secure} on session cookies. Turn off for local HTTP dev.
     */
    @Overwrite(env = @Env)
    private boolean secureCookies = true;

    public int port() {
        return port;
    }

    public String host() {
        return host;
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    /** Non-empty frontend base URL, defaulting to {@link #publicBaseUrl()}. Always without a trailing slash. */
    public String frontendBaseUrl() {
        String url = frontendBaseUrl == null || frontendBaseUrl.isBlank() ? publicBaseUrl : frontendBaseUrl;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String sessionCookieName() {
        return sessionCookieName;
    }

    public int sessionMaxAgeDays() {
        return sessionMaxAgeDays;
    }

    public String tokenEncryptionKey() {
        return tokenEncryptionKey;
    }

    public int guildRecheckTtlMinutes() {
        return guildRecheckTtlMinutes;
    }

    public int guildRecheckTtlBackgroundMinutes() {
        return guildRecheckTtlBackgroundMinutes;
    }

    public int sweeperIntervalMinutes() {
        return sweeperIntervalMinutes;
    }

    public boolean secureCookies() {
        return secureCookies;
    }
}
