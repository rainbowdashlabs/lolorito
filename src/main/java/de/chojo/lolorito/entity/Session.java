/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import java.time.Instant;

/**
 * Row from {@code lolorito.session}. Access + refresh tokens stay encrypted
 * at rest; the caller unwraps them with the {@code TokenCipher}.
 */
public record Session(
        String id,
        long discordUserId,
        Instant createdAt,
        Instant expiresAt,
        String userAgent,
        byte[] accessTokenCiphertext,
        byte[] accessTokenIv,
        Instant accessExpiresAt,
        byte[] refreshTokenCiphertext,
        byte[] refreshTokenIv,
        boolean membershipOk,
        Instant membershipCheckedAt) {}
