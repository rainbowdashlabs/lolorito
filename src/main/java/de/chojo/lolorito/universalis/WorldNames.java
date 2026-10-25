/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.universalis;

import de.chojo.universalis.worlds.Worlds;

/**
 * Display names for world and data center ids. {@link Worlds#worldById} never
 * returns null: an unknown id comes back as a world with a blank name, so the
 * id itself is used whenever the name is missing or blank. An unknown data
 * center falls back to {@code DC <id>}.
 */
public final class WorldNames {

    private WorldNames() {}

    public static String nameOf(int worldId) {
        var world = Worlds.worldById(worldId);
        return world == null || world.name() == null || world.name().isBlank() ? String.valueOf(worldId) : world.name();
    }

    public static String dataCenterNameOf(int dataCenterId) {
        var dataCenter = Worlds.datacenterById(dataCenterId);
        return dataCenter == null
                        || dataCenter.name() == null
                        || dataCenter.name().isBlank()
                ? "DC " + dataCenterId
                : dataCenter.name();
    }
}
