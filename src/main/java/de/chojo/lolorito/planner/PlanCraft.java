/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

import de.chojo.lolorito.value.Valuation;

import java.util.List;

/**
 * One craft the plan wants performed at home. Instead of a fictional
 * "buy the product at ingredient cost" line, the SPA renders this as a
 * bill of materials (what to buy, where), the intermediate crafts to
 * perform first, and the finished product to list — plus the spread
 * between what the materials cost and what the product is expected to
 * fetch.
 *
 * @param uniqueKey     matches the CRAFT candidate's key ({@code craft:<recipeId>})
 * @param itemId        the finished product
 * @param itemName      display label for the product
 * @param qty           units produced by one craft run (the recipe yield)
 * @param craftClass    required crafter class
 * @param craftLevel    required class level
 * @param craftVerified false when the craft came from the no-stored-skills fallback
 * @param materials     what to buy and where — one line per (item, world)
 * @param intermediates sub-crafts to perform before the head craft, in craft order
 * @param materialsCost total gil for the bill of materials
 * @param valuation     product valuation for {@code qty} units; {@code evGross}
 *                      is the expected profit over {@code materialsCost}
 */
public record PlanCraft(
        String uniqueKey,
        int itemId,
        String itemName,
        int qty,
        String craftClass,
        int craftLevel,
        boolean craftVerified,
        List<PlanCraftMaterial> materials,
        List<PlanCraftStep> intermediates,
        long materialsCost,
        Valuation valuation) {}
