/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

/**
 * Who can read a saved basket via its share token.
 *
 * <ul>
 *   <li>{@link #PRIVATE} — no share access; only the owner can fetch it.</li>
 *   <li>{@link #AUTHENTICATED} — any signed-in user with the token.</li>
 *   <li>{@link #PUBLIC} — anyone with the token (no session required).</li>
 * </ul>
 */
public enum BasketVisibility {
    PRIVATE,
    AUTHENTICATED,
    PUBLIC;

    public static BasketVisibility fromWire(String s) {
        if (s == null) return PRIVATE;
        return switch (s.toLowerCase()) {
            case "public" -> PUBLIC;
            case "authenticated" -> AUTHENTICATED;
            default -> PRIVATE;
        };
    }

    public String wire() {
        return name().toLowerCase();
    }
}
