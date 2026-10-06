/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'
import type { Valuation } from '@/api/offers'

/** Effective sourcing choice for a craftable node, as an override wire value. */
export type ShoppingDecision = 'BUY' | 'CRAFT'

/** One buy line inside a shopping stop. */
export interface ShoppingLine {
  itemId: number
  itemName: string
  /** True when the buy is priced on the HQ board. */
  hq: boolean
  qty: number
  unitPrice: number
  totalCost: number
  worldId: number
  worldName: string
  canBeHq: boolean
}

/** World-grouped part of the shopping run — biggest spend first. */
export interface ShoppingStop {
  worldId: number
  worldName: string
  cost: number
  lines: ShoppingLine[]
}

/** An intermediate to craft before the head craft, in dependency order. */
export interface ShoppingPreCraft {
  itemId: number
  itemName: string
  qty: number
  craftClass: string
  craftLevel: number
}

/**
 * One node of the ingredient tree. `decision` is the effective buy/craft
 * choice (server default = cheaper side); craftable nodes can be flipped
 * via `ShoppingRequest.overrides`.
 */
export interface ShoppingNode {
  itemId: number
  itemName: string
  qty: number
  canBeHq: boolean
  /** True when the buy is priced on the HQ board (only when canBeHq). */
  hq: boolean
  craftable: boolean
  decision: 'buy' | 'craft'
  buyUnitCost: number | null
  craftUnitCost: number | null
  craftClass: string | null
  craftLevel: number | null
  children: ShoppingNode[]
}

/**
 * Full quick-shopping plan for one product. `valuation` is always-HQ over
 * `count` products: `expectedNet` is PER UNIT net of tax; `evGross` is the
 * expected profit over `totalCost`.
 */
/** One recipe that produces the requested item. */
export interface ShoppingRecipeOption {
  recipeId: number
  craftClass: string
  level: number
  yield: number
}

export interface ShoppingPlan {
  itemId: number
  itemName: string
  count: number
  runs: number
  yield: number
  recipeId: number
  craftClass: string
  craftLevel: number
  productCanBeHq: boolean
  /** Every recipe for the product; more than one means the caller can switch. */
  recipes: ShoppingRecipeOption[]
  stops: ShoppingStop[]
  preCrafts: ShoppingPreCraft[]
  tree: ShoppingNode[]
  totalCost: number
  valuation: Valuation | null
}

export interface ShoppingRequest {
  itemId: number
  recipeId?: number
  /** Finished items wanted (default 1). */
  count?: number
  /** Defaults server-side to the saved filter's home world. */
  homeWorld?: number
  /** Item ids whose buys should be priced on the HQ board. */
  hqItemIds?: number[]
  /** Per-item buy/craft overrides on top of the server's cheaper-side default. */
  overrides?: Record<number, ShoppingDecision>
}

export async function plan(req: ShoppingRequest): Promise<ShoppingPlan> {
  const { data } = await apiClient.post<ShoppingPlan>('/shopping', req)
  return data
}
