/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { PlanBuy, RetainerPick } from '@/api/planner'

export interface SellRecommendation {
  itemId: number
  itemName: string
  hq: boolean
  qty: number
  slots: number
  listAt: number
  expectedNet: number
  suggestedStackSize: number
  stacks: number
}

/** Retail marketboard tax we back out of the expected-net figure. */
const MB_TAX = 0.05

/**
 * Split target (units per retainer listing). Small stacks clear faster
 * because more buyers fit inside their own carry budget — 20 is the
 * classic "10× 20 sells before 1× 200" heuristic.
 */
const SELL_STACK_TARGET = 20

/**
 * Back out the list-side price from the model's expected net. The
 * backend's `expectedNet` is PER UNIT (`evGross = (expectedNet − cost) ×
 * qty`), so the list price is simply the per-unit net grossed back up by
 * the tax — no quantity involved. (The old version divided by qty and
 * understated the price for every multi-unit buy.)
 */
export function listPriceFor(buy: { valuation: { expectedNet: number } }): number {
  const perUnit = buy.valuation.expectedNet
  if (perUnit <= 0) return 0
  return Math.max(1, Math.round(perUnit / (1 - MB_TAX)))
}

/** Pick a per-listing stack size that keeps each listing ≤ SELL_STACK_TARGET units. */
export function suggestedStackSize(qty: number): number {
  if (qty <= 0) return 1
  if (qty <= SELL_STACK_TARGET) return qty
  return Math.max(1, Math.ceil(qty / Math.ceil(qty / SELL_STACK_TARGET)))
}

/**
 * Null for lines that aren't sold as-is — MATERIAL buys have no own
 * valuation (their value flows through the craft they feed).
 */
export function recommendationFor(buy: PlanBuy | RetainerPick): SellRecommendation | null {
  const valuation = buy.valuation
  if (!valuation) return null
  const stackSize = suggestedStackSize(buy.qty)
  const stacks = Math.ceil(buy.qty / stackSize)
  return {
    itemId: buy.itemId,
    itemName: buy.itemName,
    hq: buy.hq,
    qty: buy.qty,
    slots: buy.slots,
    listAt: listPriceFor({ valuation }),
    // Total expected net for the whole line — expectedNet is per unit.
    expectedNet: valuation.expectedNet * buy.qty,
    suggestedStackSize: stackSize,
    stacks,
  }
}
