/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

/**
 * Standard JSON error body sent to clients when a request fails. Kept
 * intentionally small — two fields keeps clients easy to write, and
 * anything else the caller might need (validation details, trace ids)
 * belongs in a purpose-built response type.
 *
 * @param error   short category (e.g. {@code "Invalid Input"}, {@code "Not Found"})
 * @param message human-readable detail, or {@code null}
 */
public record ErrorResponse(String error, String message) {

    public ErrorResponse(String error) {
        this(error, null);
    }
}
