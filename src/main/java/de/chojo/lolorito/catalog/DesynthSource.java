/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.catalog;

import java.util.List;

/**
 * One row of the desynth seed. {@code desynthClass} + {@code desynthLevel}
 * are the FFXIV-standard gate (item's repair class + ilvl); nullable for
 * rows whose class/level XIVAPI didn't fill in.
 */
public record DesynthSource(int sourceItemId, String desynthClass, Integer desynthLevel, List<Component> components) {

    public record Component(int componentItemId, double avgQty) {}
}
