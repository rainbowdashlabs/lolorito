/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

/**
 * Where an {@link AlertRule} watches — a single world or a whole data
 * center. Exactly one of {@link #worldId()} / {@link #dataCenterId()} is
 * non-null; the DB CHECK constraint enforces the same on the row.
 */
public record AlertScope(Integer worldId, Integer dataCenterId) {

    public static AlertScope forWorld(int worldId) {
        return new AlertScope(worldId, null);
    }

    public static AlertScope forDataCenter(int dataCenterId) {
        return new AlertScope(null, dataCenterId);
    }

    public boolean isWorld() {
        return worldId != null;
    }

    public boolean isDataCenter() {
        return dataCenterId != null;
    }
}
