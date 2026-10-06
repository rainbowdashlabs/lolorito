/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner.ilp;

/**
 * One 0/1 pick fed into {@link KnapsackSolver}. Each item consumes three
 * capacities and yields one value; the "group" links picks that are mutually
 * exclusive (only one candidate per item id may survive).
 *
 * @param id             payload — the solver echoes it back on the
 *                       winning picks
 * @param group          mutual-exclusion class (item id in practice)
 * @param value          gil the objective adds when this item is chosen
 * @param cost           gil spent — capped by {@code budget}
 * @param qty            units consumed — capped by {@code inventorySlots}
 * @param attentionHours attention consumed — capped by {@code
 *                       attentionBudgetHours}
 */
public record KnapsackItem(Object id, int group, double value, long cost, int qty, double attentionHours) {}
