/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface TrendRow {
  itemId: number
  itemName: string
  hq: boolean
  /** Units/day gained (positive) or lost (negative) per day — OLS slope. */
  slope: number
  /** Slope ÷ mean units/day — momentum as a fraction of the item's own volume. */
  relativeSlope: number
  r2: number
  /** True when r² is too low to trust the fitted line. */
  weakFit: boolean
  totalUnits: number
  avgUnitsPerDay: number
  lastDayUnits: number
  /** Regression forecast of units sold in the next 24 hours (clamped ≥ 0). */
  predictedNext24h: number
}

export interface TrendBoard {
  homeWorldId: number
  windowDays: number
  fittedKeys: number
  trending: TrendRow[]
  losing: TrendRow[]
}

export async function board(params: { homeWorld?: number; window?: number; limit?: number } = {}): Promise<TrendBoard> {
  const q: Record<string, string> = {}
  if (params.homeWorld != null) q.home_world = String(params.homeWorld)
  if (params.window != null) q.window = String(params.window)
  if (params.limit != null) q.limit = String(params.limit)
  const { data } = await apiClient.get<TrendBoard>('/trends', { params: q })
  return data
}

/** Trend for a single item on the home world; null when it has no sales in the window (204). */
export async function itemTrend(
  itemId: number,
  params: { homeWorld?: number; hq?: boolean; window?: number } = {},
): Promise<TrendRow | null> {
  const q: Record<string, string> = {}
  if (params.homeWorld != null) q.home_world = String(params.homeWorld)
  if (params.hq != null) q.hq = String(params.hq)
  if (params.window != null) q.window = String(params.window)
  const { data, status } = await apiClient.get<TrendRow | null>(`/items/${itemId}/trend`, { params: q })
  return status === 204 ? null : data
}

/** Units and gil sold on one world inside one clock hour. */
export interface HourBucket {
  hourStart: string
  units: number
  gil: number
}

/** Sales on one world over the last 24 hours, oldest hour first. */
export interface WorldActivity {
  worldId: number
  totalUnits: number
  totalGil: number
  hours: HourBucket[]
}

export async function worldActivity(worldId: number): Promise<WorldActivity> {
  const { data } = await apiClient.get<WorldActivity>(`/worlds/${worldId}/sales/24h`)
  return data
}
