/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

/**
 * The action kinds the planner may mix into one basket. RESALE,
 * DESYNTH and CRAFT are candidate lanes the solver picks from;
 * {@link #MATERIAL} never enters the ILP — it's the stop-line shape a
 * chosen CRAFT's bill of materials takes when the buys are folded into
 * the route (buy these here, they feed a craft at home).
 */
public enum PlanAction {
    RESALE,
    DESYNTH,
    CRAFT,
    MATERIAL
}
