/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.value;

import java.time.Instant;

/**
 * One recorded sale on the market board — the atom of every fit.
 *
 * @param unitPrice gil per unit at time of sale
 * @param quantity  how many units cleared in this sale
 * @param at        when it cleared (server time)
 */
public record SaleObservation(int unitPrice, int quantity, Instant at) {}
