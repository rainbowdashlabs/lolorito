/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

/**
 * One line of a {@link PlanCraft} bill of materials — buy {@code qty} of
 * {@code itemId} on {@code worldName}. When the cheap side of an item's
 * book spans several worlds the BOM carries one line per world, so the
 * player knows exactly how many units to pick up at each stop.
 *
 * @param unitPrice blended per-unit price across the listings taken on
 *                  this world (display value; {@code totalCost} is exact)
 */
public record PlanCraftMaterial(
        int itemId, String itemName, int qty, int unitPrice, long totalCost, int worldId, String worldName) {}
