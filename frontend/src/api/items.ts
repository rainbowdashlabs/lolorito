/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface ItemModelDto {
  expectedPrice: number
  medianPrice: number
  sigma: number
  lambdaAggressive: number
  lambdaMedian: number
  lambdaAbove: number
  lambdaUndercut: number
  ghostFraction: number
  sampleCount: number
  sufficient: boolean
  /** True when the fit fell back to DC-pooled sales — lower confidence. */
  pooled: boolean
  fittedAt: string
}

export interface ItemListingDto {
  worldId: number
  worldName: string
  unitPrice: number
  quantity: number
  hq: boolean
  reviewedAt: string
}

export interface ValuationDto {
  expectedNet: number
  sigmaNet: number
  evGross: number
  expectedTimeOnShelfHours: number
  evPerHour: number
  /** Recommended list price as a multiple of the market median. */
  listRatio: number
}

export interface DesynthComponentDto {
  itemId: number
  itemName: string
  avgQty: number
  modelSufficient: boolean
  expectedNet: number
}

export interface DesynthBreakdown {
  /** Crafter class required to desynth (null for Teamcraft-fallback rows). */
  desynthClass: string | null
  /** Minimum desynth level required, or null when unknown. */
  desynthLevel: number | null
  components: DesynthComponentDto[]
  valuation: ValuationDto | null
}

export type ChosenSource = 'buy' | 'craft' | 'unknown'

export interface CraftIngredientDto {
  itemId: number
  itemName: string
  quantity: number
  cheapestBuy: number | null
  craftPerUnit: number | null
  chosenSource: ChosenSource
  subIngredients: CraftIngredientDto[] | null
  /** Sub-recipe class (carpenter, blacksmith, …) or null when buy-only. */
  subRecipeClass: string | null
  /** Sub-recipe level, or null when there's no sub-recipe. */
  subRecipeLevel: number | null
}

export interface CraftBreakdown {
  recipeId: number
  craftClass: string
  level: number
  yield: number
  ingredients: CraftIngredientDto[]
  valuation: ValuationDto | null
}

export interface ItemDetailDto {
  itemId: number
  itemName: string
  /** UI category name — "Crystal", "Reagent", "Culinarian's Primary Tool"… */
  category: string
  /** In-game flavour text. Empty when XIVAPI doesn't carry it. */
  description: string
  hq: boolean
  homeWorldId: number
  homeWorldName: string
  dataCenter: string
  model: ItemModelDto | null
  listings: ItemListingDto[]
  desynth: DesynthBreakdown | null
  crafts: CraftBreakdown[]
}

export async function detail(itemId: number, hq: boolean, homeWorld?: number): Promise<ItemDetailDto> {
  const params: Record<string, string> = { hq: hq ? 'true' : 'false' }
  if (homeWorld != null) params.home_world = String(homeWorld)
  const { data } = await apiClient.get<ItemDetailDto>(`/items/${itemId}`, { params })
  return data
}

export interface SalesBucket {
  day: string
  sales: number
  units: number
  avgPrice: number
  minPrice: number
  maxPrice: number
}

/** 7-day (or {@code days}) sales aggregate for a (world, item, hq) key. */
export async function salesHistory(
  itemId: number,
  hq: boolean,
  homeWorld?: number,
  days = 7,
): Promise<SalesBucket[]> {
  const params: Record<string, string> = { hq: hq ? 'true' : 'false', days: String(days) }
  if (homeWorld != null) params.home_world = String(homeWorld)
  const { data } = await apiClient.get<SalesBucket[]>(`/items/${itemId}/sales-history`, { params })
  return data
}
