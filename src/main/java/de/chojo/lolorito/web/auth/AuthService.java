/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.auth;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.core.Discord;
import de.chojo.lolorito.entity.Session;
import de.chojo.lolorito.repository.Sessions;
import de.chojo.lolorito.web.auth.DiscordOAuthClient.DiscordUser;
import de.chojo.lolorito.web.auth.DiscordOAuthClient.Token;
import org.slf4j.Logger;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Business logic for the Discord OAuth login: minting new sessions, running
 * the per-request revalidation ladder, and tearing sessions down at logout.
 * <p>
 * The revalidation ladder is the load-bearing piece: every request that
 * carries a session cookie flows through {@link #revalidate(Session)}. It
 * refreshes the Discord access token if the safety window has elapsed, and
 * re-checks guild membership if the recheck TTL has expired. On any failure
 * — refused refresh, user no longer in the guild — the session is deleted
 * and {@link #revalidate(Session)} returns empty so the caller can 401.
 */
@Singleton
public class AuthService {
    private static final Logger log = getLogger(AuthService.class);
    private static final Duration ACCESS_REFRESH_WINDOW = Duration.ofMinutes(5);

    private final File config;
    private final Sessions sessions;
    private final DiscordOAuthClient discord;
    private final TokenCipher cipher;
    private final long guildId;
    private final SecureRandom random = new SecureRandom();
    /**
     * Deferred so we don't create a Discord ↔ AuthService cycle at wiring
     * time. Nullable in tests where no Discord instance is bound.
     */
    private final Provider<Discord> discordBot;
    /**
     * Very small backoff: when Discord's OAuth API rate-limits us, sit out
     * calls until this instant passes. Set from the observed retry-after
     * on a 429 response.
     */
    private volatile Instant apiCooldownUntil = Instant.EPOCH;

    @Inject
    public AuthService(
            File config,
            Sessions sessions,
            DiscordOAuthClient discord,
            TokenCipher cipher,
            Provider<Discord> discordBot) {
        this.config = config;
        this.sessions = sessions;
        this.discord = discord;
        this.cipher = cipher;
        this.discordBot = discordBot;
        this.guildId = config.baseSettings().botGuild();
    }

    /**
     * Random opaque token to use as the OAuth {@code state} parameter and cookie.
     */
    public String newOauthState() {
        var bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String authorizeUrl(String state) {
        return discord.authorizeUrl(state);
    }

    /**
     * Completes the OAuth callback: exchanges code for tokens, verifies the
     * user is in the configured guild, and mints a session row. Returns the
     * opaque session id to put in the cookie.
     *
     * @throws AuthException if guild membership check fails
     */
    public String completeLogin(String code, String userAgent) {
        Token token = discord.exchangeCode(code);
        DiscordUser user = discord.fetchUser(token.accessToken());

        if (!isInGuild(token.accessToken())) {
            discord.revoke(token.accessToken());
            throw new AuthException("Not a guild member");
        }

        var now = Instant.now();
        var sealedAccess = cipher.encrypt(token.accessToken());
        var sealedRefresh = cipher.encrypt(token.refreshToken());
        var session = new Session(
                UUID.randomUUID().toString(),
                user.id(),
                now,
                now.plus(Duration.ofDays(config.http().sessionMaxAgeDays())),
                userAgent,
                sealedAccess.ciphertext(),
                sealedAccess.iv(),
                now.plusSeconds(token.expiresInSeconds()),
                sealedRefresh.ciphertext(),
                sealedRefresh.iv(),
                true,
                now);
        sessions.create(session);
        log.info("Discord login: minted session for user {}", user.id());
        return session.id();
    }

    /**
     * Returns the session if still valid, empty if it isn't (and cleans up
     * server-side state as a side effect). This is the hot path — most calls
     * hit the fast branch where both freshness windows are still open.
     */
    public Optional<Session> revalidate(Session session) {
        var now = Instant.now();
        if (session.expiresAt().isBefore(now)) {
            sessions.delete(session.id());
            return Optional.empty();
        }

        Session current = session;

        // Ladder step 1 — access token freshness. On a transient failure
        // (network blip, Discord 5xx, rate limit) we keep the session alive
        // with the old token and let the next request retry, rather than
        // logging the user out for something that's not their fault.
        if (current.accessExpiresAt().isBefore(now.plus(ACCESS_REFRESH_WINDOW))) {
            RefreshOutcome outcome = refreshTokens(current);
            if (outcome instanceof RefreshOutcome.Ok(Session session1)) {
                current = session1;
            } else if (outcome instanceof RefreshOutcome.Transient) {
                log.debug("Refresh transient failure for session {} — keeping existing token", current.id());
                return Optional.of(current);
            } else {
                return Optional.empty();
            }
        }

        // Ladder step 2 — guild membership TTL.
        var recheckTtl = Duration.ofMinutes(config.http().guildRecheckTtlMinutes());
        if (current.membershipCheckedAt().plus(recheckTtl).isBefore(now)) {
            // Cheap path: ask the bot. GUILD_MEMBERS intent is enabled, so
            // the JDA cache is authoritative. No Discord HTTP call needed.
            var viaBot = memberViaBot(current.discordUserId());
            Boolean ok = viaBot.orElse(null);
            if (ok == null) {
                // Discord's OAuth /users/@me/guilds is rate-limited harshly.
                // If we recently got a 429 we skip the check this cycle
                // rather than log the user out.
                if (apiCoolingDown(now)) {
                    return Optional.of(current);
                }
                String accessToken = cipher.decrypt(current.accessTokenCiphertext(), current.accessTokenIv());
                try {
                    ok = isInGuild(accessToken);
                } catch (DiscordOAuthClient.DiscordOAuthException e) {
                    if (e.getMessage() != null && e.getMessage().contains("429")) {
                        log.info("Rate limited by Discord; skipping membership check for session {}", current.id());
                        observeApiRateLimit(Duration.ofSeconds(30));
                        return Optional.of(current);
                    }
                    log.warn("Guild-membership check failed for session {}: {}", current.id(), e.getMessage());
                    sessions.delete(current.id());
                    return Optional.empty();
                } catch (Exception e) {
                    log.warn("Guild-membership check failed for session {}: {}", current.id(), e.getMessage());
                    sessions.delete(current.id());
                    return Optional.empty();
                }
            }
            if (!ok) {
                log.info("Session {} revoked: user {} left the guild", current.id(), current.discordUserId());
                sessions.delete(current.id());
                return Optional.empty();
            }
            sessions.updateMembership(current.id(), true, now);
        }

        // Slide the outer expiry on activity.
        sessions.touchExpiry(
                current.id(), now.plus(Duration.ofDays(config.http().sessionMaxAgeDays())));

        return Optional.of(current);
    }

    public void logout(Session session) {
        try {
            String accessToken = cipher.decrypt(session.accessTokenCiphertext(), session.accessTokenIv());
            discord.revoke(accessToken);
        } catch (Exception e) {
            log.debug("Token revoke on logout failed (non-fatal): {}", e.getMessage());
        }
        sessions.delete(session.id());
    }

    private RefreshOutcome refreshTokens(Session session) {
        String refresh = cipher.decrypt(session.refreshTokenCiphertext(), session.refreshTokenIv());
        Token fresh;
        try {
            fresh = discord.refresh(refresh);
        } catch (DiscordOAuthClient.DiscordOAuthException e) {
            if (isTransient(e)) {
                return new RefreshOutcome.Transient();
            }
            log.info("Session {} revoked: Discord refused refresh ({})", session.id(), e.getMessage());
            sessions.delete(session.id());
            return new RefreshOutcome.Revoked();
        } catch (Exception e) {
            // Anything not from DiscordOAuthClient — treat as transient too;
            // we'd rather retry next request than kick a real user.
            log.warn("Session {} refresh failed transiently ({})", session.id(), e.getMessage());
            return new RefreshOutcome.Transient();
        }
        var now = Instant.now();
        var sealedAccess = cipher.encrypt(fresh.accessToken());
        var sealedRefresh = cipher.encrypt(fresh.refreshToken());
        sessions.updateTokens(
                session.id(),
                sealedAccess.ciphertext(),
                sealedAccess.iv(),
                now.plusSeconds(fresh.expiresInSeconds()),
                sealedRefresh.ciphertext(),
                sealedRefresh.iv());
        return new RefreshOutcome.Ok(new Session(
                session.id(),
                session.discordUserId(),
                session.createdAt(),
                session.expiresAt(),
                session.userAgent(),
                sealedAccess.ciphertext(),
                sealedAccess.iv(),
                now.plusSeconds(fresh.expiresInSeconds()),
                sealedRefresh.ciphertext(),
                sealedRefresh.iv(),
                session.membershipOk(),
                session.membershipCheckedAt()));
    }

    /**
     * Classify a refresh failure. A 5xx / 429 / network error is transient;
     * anything with {@code invalid_grant} / {@code unauthorized_client} in
     * the response body is a hard revocation from Discord's side.
     */
    private static boolean isTransient(DiscordOAuthClient.DiscordOAuthException e) {
        String msg = e.getMessage();
        if (msg == null) return true;
        String lower = msg.toLowerCase();
        if (lower.contains("invalid_grant") || lower.contains("unauthorized_client")) return false;
        if (lower.contains("returned 5") || lower.contains("429") || lower.contains("failed")) return true;
        // Unknown 4xx that isn't an explicit revocation — err on the side of
        // keeping the user signed in and letting the next refresh retry.
        return true;
    }

    /** Sum type for the refresh path so revalidate can distinguish revoke vs. retry. */
    private sealed interface RefreshOutcome {
        record Ok(Session session) implements RefreshOutcome {}

        record Transient() implements RefreshOutcome {}

        record Revoked() implements RefreshOutcome {}
    }

    private boolean isInGuild(String accessToken) {
        long[] ids = discord.fetchGuildIds(accessToken);
        for (long id : ids) if (id == guildId) return true;
        return false;
    }

    /**
     * Ask the JDA shard manager whether {@code discordUserId} is currently a
     * member of the configured bot guild. Returns:
     * <ul>
     *   <li>{@code Optional.of(true)} — cache confirms membership</li>
     *   <li>{@code Optional.empty()} — bot not ready, guild not cached, or
     *       the member cache doesn't have this user. This is deliberately
     *       vague: JDA's member cache can miss legitimate members after a
     *       gateway restart (chunking still in flight) or when a member is
     *       offline. Returning empty makes the caller fall back to the
     *       authoritative OAuth check instead of wrongly revoking the
     *       session.</li>
     * </ul>
     * There is no {@code Optional.of(false)} return — a definitive "not a
     * member" only ever comes from the OAuth authoritative path or from
     * {@link de.chojo.lolorito.discord.listener.SessionEvictionListener}'s live {@code GuildMemberRemoveEvent}.
     */
    private Optional<Boolean> memberViaBot(long discordUserId) {
        try {
            Discord bot = discordBot.get();
            if (bot == null) return Optional.empty();
            var shardManager = bot.shardManager();
            if (shardManager == null) return Optional.empty();
            var guild = shardManager.getGuildById(guildId);
            if (guild == null) return Optional.empty();
            var member = guild.getMemberById(discordUserId);
            return member != null ? Optional.of(true) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** True iff we're currently sitting out an API-rate-limit backoff. */
    boolean apiCoolingDown(Instant now) {
        return now.isBefore(apiCooldownUntil);
    }

    void observeApiRateLimit(Duration retryAfter) {
        apiCooldownUntil = Instant.now().plus(retryAfter);
    }

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
