/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

import java.time.Instant;

/**
 * One own-retainer sale as logged by the user. Ships as the JSON body of
 * {@code GET /api/v1/me/retainer-sales} and is written by
 * {@code POST /api/v1/me/retainer-sales}.
 *
 * @param id             surrogate key from the DB
 * @param discordUserId  owner
 * @param retainerName   free-form retainer name (nullable)
 * @param itemId         item that sold
 * @param worldId        world it sold on
 * @param hq             quality of the sold stack
 * @param unitPrice      price per unit
 * @param quantity       units sold
 * @param soldAt         when the sale completed
 * @param note           free-form note (nullable)
 */
public record RetainerSale(
        long id,
        long discordUserId,
        String retainerName,
        int itemId,
        int worldId,
        boolean hq,
        long unitPrice,
        int quantity,
        Instant soldAt,
        String note) {}
