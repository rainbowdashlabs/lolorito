/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.Session;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * SQL access to {@code lolorito.session}. Business rules live in {@code AuthService}.
 */
@Singleton
public class Sessions {
    @Inject
    public Sessions(DataSource dataSource) {
        // sadu 2 uses the default QueryConfiguration
    }

    private static Session readRow(Row row) throws SQLException {
        return new Session(
                row.getString("id"),
                row.getLong("discord_user_id"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("expires_at", INSTANT_TIMESTAMP),
                row.getString("user_agent"),
                row.getBytes("access_token_ct"),
                row.getBytes("access_token_iv"),
                row.get("access_expires_at", INSTANT_TIMESTAMP),
                row.getBytes("refresh_token_ct"),
                row.getBytes("refresh_token_iv"),
                row.getBoolean("membership_ok"),
                row.get("membership_checked_at", INSTANT_TIMESTAMP));
    }

    public void create(Session s) {
        query("""
                INSERT INTO session (id, discord_user_id, created_at, expires_at, user_agent,
                                     access_token_ct, access_token_iv, access_expires_at,
                                     refresh_token_ct, refresh_token_iv,
                                     membership_ok, membership_checked_at)
                VALUES (:id, :user_id, :created_at, :expires_at, :user_agent,
                        :access_ct, :access_iv, :access_expires_at,
                        :refresh_ct, :refresh_iv,
                        :ok, :checked_at)
                """)
                .single(call().bind("id", s.id())
                        .bind("user_id", s.discordUserId())
                        .bind("created_at", s.createdAt(), INSTANT_TIMESTAMP)
                        .bind("expires_at", s.expiresAt(), INSTANT_TIMESTAMP)
                        .bind("user_agent", s.userAgent())
                        .bind("access_ct", s.accessTokenCiphertext())
                        .bind("access_iv", s.accessTokenIv())
                        .bind("access_expires_at", s.accessExpiresAt(), INSTANT_TIMESTAMP)
                        .bind("refresh_ct", s.refreshTokenCiphertext())
                        .bind("refresh_iv", s.refreshTokenIv())
                        .bind("ok", s.membershipOk())
                        .bind("checked_at", s.membershipCheckedAt(), INSTANT_TIMESTAMP))
                .insert();
    }

    public Optional<Session> find(String id) {
        return query("SELECT * FROM session WHERE id = :id")
                .single(call().bind("id", id))
                .map(Sessions::readRow)
                .first();
    }

    /** All active sessions owned by a user, newest first. Used by the Settings sessions list. */
    public java.util.List<Session> listByUser(long discordUserId) {
        return query("SELECT * FROM session WHERE discord_user_id = :u ORDER BY created_at DESC")
                .single(call().bind("u", discordUserId))
                .map(Sessions::readRow)
                .all();
    }

    public void touchExpiry(String id, Instant expiresAt) {
        query("UPDATE session SET expires_at = :exp WHERE id = :id")
                .single(call().bind("exp", expiresAt, INSTANT_TIMESTAMP).bind("id", id))
                .update();
    }

    public void updateTokens(
            String id, byte[] accessCt, byte[] accessIv, Instant accessExpiresAt, byte[] refreshCt, byte[] refreshIv) {
        query("""
                UPDATE session
                   SET access_token_ct = :ac, access_token_iv = :ai, access_expires_at = :ae,
                       refresh_token_ct = :rc, refresh_token_iv = :ri
                 WHERE id = :id
                """)
                .single(call().bind("ac", accessCt)
                        .bind("ai", accessIv)
                        .bind("ae", accessExpiresAt, INSTANT_TIMESTAMP)
                        .bind("rc", refreshCt)
                        .bind("ri", refreshIv)
                        .bind("id", id))
                .update();
    }

    public void updateMembership(String id, boolean ok, Instant checkedAt) {
        query("UPDATE session SET membership_ok = :ok, membership_checked_at = :at WHERE id = :id")
                .single(call().bind("ok", ok)
                        .bind("at", checkedAt, INSTANT_TIMESTAMP)
                        .bind("id", id))
                .update();
    }

    public void delete(String id) {
        query("DELETE FROM session WHERE id = :id")
                .single(call().bind("id", id))
                .delete();
    }

    public int deleteAllForUser(long discordUserId) {
        return query("DELETE FROM session WHERE discord_user_id = :user_id")
                .single(call().bind("user_id", discordUserId))
                .delete()
                .rows();
    }

    public int deleteExpired(Instant cutoff) {
        return query("DELETE FROM session WHERE expires_at < :cutoff")
                .single(call().bind("cutoff", cutoff, INSTANT_TIMESTAMP))
                .delete()
                .rows();
    }

    public List<Session> dueForBackgroundRecheck(Instant threshold, int limit) {
        return query("""
                SELECT * FROM session
                 WHERE membership_ok = TRUE
                   AND membership_checked_at < :threshold
                 ORDER BY membership_checked_at ASC
                 LIMIT :lim
                """)
                .single(call().bind("threshold", threshold, INSTANT_TIMESTAMP).bind("lim", limit))
                .map(Sessions::readRow)
                .all();
    }
}
