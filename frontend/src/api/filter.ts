/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface FilterRow {
  worldId: number
  offerLimit: number
  unitPrice: number
  factor: number
  refreshHours: number
  popularity: number
  marketVolume: number
  interest: number
  sales: number
  views: number
  profit: number
  effectiveProfit: number
  target: string
}

export type FilterPatch = Partial<FilterRow>

export async function get(): Promise<FilterRow> {
  const { data } = await apiClient.get<FilterRow>('/me/filter')
  return data
}

export async function put(patch: FilterPatch): Promise<FilterRow> {
  const { data } = await apiClient.put<FilterRow>('/me/filter', patch)
  return data
}

/** Blow away the caller's saved filter and reset it to defaults. */
export async function reset(): Promise<FilterRow> {
  const { data } = await apiClient.delete<FilterRow>('/me/filter')
  return data
}
