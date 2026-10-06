/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'
import type { Valuation } from '@/api/offers'

export type PlanAction = 'RESALE' | 'DESYNTH' | 'CRAFT' | 'MATERIAL'

export interface PlanBuy {
  uniqueKey: string
  itemId: number
  itemName: string
  hq: boolean
  qty: number
  slots: number
  buyPrice: number
  action: PlanAction
  /** Null for MATERIAL lines — their value flows through the craft they feed. */
  valuation: Valuation | null
  /** Required class for CRAFT/DESYNTH lines, null otherwise. */
  craftClass: string | null
  /** Required class level for CRAFT/DESYNTH lines, null otherwise. */
  craftLevel: number | null
  /** False when the requirement came from the no-stored-skills fallback. */
  craftVerified: boolean
}

export interface PlanStop {
  worldId: number
  worldName: string
  dataCenterId: number
  dataCenterName: string
  hopSecondsFromPrev: number
  totalQty: number
  totalSlots: number
  buyCost: number
  buys: PlanBuy[]
}

/** One bill-of-materials line — buy `qty` of the item on `worldName`. */
export interface PlanCraftMaterial {
  itemId: number
  itemName: string
  qty: number
  unitPrice: number
  totalCost: number
  worldId: number
  worldName: string
}

/** An intermediate craft to perform before the head recipe consumes it. */
export interface PlanCraftStep {
  itemId: number
  itemName: string
  qty: number
  craftClass: string
  craftLevel: number
}

/**
 * A craft the plan wants performed at home: the bill of materials to buy,
 * the intermediates to pre-craft, and the finished product to list.
 * `valuation.evGross` is the expected profit over `materialsCost`.
 */
export interface PlanCraft {
  uniqueKey: string
  itemId: number
  itemName: string
  qty: number
  craftClass: string
  craftLevel: number
  craftVerified: boolean
  materials: PlanCraftMaterial[]
  intermediates: PlanCraftStep[]
  materialsCost: number
  valuation: Valuation
}

/** One expected component of a desynth — avgQty is a probabilistic average. */
export interface PlanDesynthOutput {
  itemId: number
  itemName: string
  avgQty: number
  /** Expected per-unit sale net at home; null when the component has no model (EV is a lower bound). */
  unitNet: number | null
}

/**
 * A desynth the plan wants performed at home. The buy itself is a regular
 * DESYNTH stop line; this carries the outputs and the pay-vs-get spread.
 * `valuation.evGross` is the expected profit over `buyCost`.
 */
export interface PlanDesynth {
  uniqueKey: string
  itemId: number
  itemName: string
  /** True when the cheaper source listing was HQ. */
  hq: boolean
  qty: number
  buyPrice: number
  buyCost: number
  sourceWorldId: number
  sourceWorldName: string
  desynthClass: string | null
  desynthLevel: number | null
  desynthVerified: boolean
  outputs: PlanDesynthOutput[]
  valuation: Valuation
}

export interface RetainerPick {
  uniqueKey: string
  itemId: number
  itemName: string
  hq: boolean
  sourceWorldId: number
  sourceWorldName: string
  dataCenterId: number
  dataCenterName: string
  qty: number
  slots: number
  buyPrice: number
  valuation: Valuation
  retainerEvPerHour: number
}

export interface Plan {
  homeWorldId: number
  homeWorldName: string
  homeDataCenterId: number
  homeDataCenterName: string
  objective: number
  totalEvGross: number
  totalBuyCost: number
  totalQty: number
  totalSlots: number
  totalAttentionHours: number
  totalHopSeconds: number
  hopSecondsToHome: number
  candidatesConsidered: number
  subsetsConsidered: number
  stops: PlanStop[]
  retainerBasket: RetainerPick[]
  retainerSpend: number
  retainerQty: number
  retainerSlots: number
  retainerEvGross: number
  crafts: PlanCraft[]
  craftMaterialsCost: number
  craftEvGross: number
  desynths: PlanDesynth[]
  desynthBuyCost: number
  desynthEvGross: number
}

export interface PlanRequest {
  homeWorld?: number
  refreshHours?: number
  budget?: number
  inventorySlots?: number
  attentionBudgetHours?: number
  attentionFraction?: number
  hopWeightGilPerSecond?: number
  maxWorlds?: number
  candidateTopK?: number
  retainerSlots?: number
  retainerListingSlots?: number
  retainerListingStackTarget?: number
  retainerAttentionFraction?: number
  retainerShelfHoursThreshold?: number
  /**
   * When true, the planner is allowed to emit CRAFT candidates gated
   * by the caller's stored per-class crafter levels. When false or
   * absent, only RESALE candidates are considered.
   */
  allowCrafts?: boolean
  /** Same shape for the DESYNTH lane, gated by stored desynth levels. */
  allowDesynth?: boolean
}

export async function plan(req: PlanRequest = {}): Promise<Plan> {
  const { data } = await apiClient.post<Plan>('/plan', req)
  return data
}

export interface ReplanRequest extends PlanRequest {
  completedWorldIds: number[]
  spentBudget: number
  usedInventory: number
}

export async function replan(req: ReplanRequest): Promise<Plan> {
  const { data } = await apiClient.post<Plan>('/plan/replan', req)
  return data
}
