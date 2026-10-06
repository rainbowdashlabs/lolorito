/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

/**
 * An intermediate craft the chain planner chose over buying — craft
 * {@code qty} of {@code itemId} before the head recipe consumes it.
 * Steps are emitted in dependency order: deepest sub-craft first.
 */
public record PlanCraftStep(int itemId, String itemName, int qty, String craftClass, int craftLevel) {}
