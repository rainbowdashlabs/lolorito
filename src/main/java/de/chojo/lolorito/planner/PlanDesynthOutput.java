/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

/**
 * One expected component of a {@link PlanDesynth}.
 *
 * @param avgQty  expected units per desynth (fractional — desynth yields
 *                are probabilistic averages)
 * @param unitNet expected sale proceeds per unit at home, net of tax;
 *                null when the component has no sufficient market model
 *                (it contributed zero to the valuation — EV is a lower
 *                bound, mirroring the desynth explorer's semantics)
 */
public record PlanDesynthOutput(int itemId, String itemName, double avgQty, Double unitNet) {}
