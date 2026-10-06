/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface DesynthCandidate {
  itemId: number
  itemName: string
  desynthClass: string | null
  desynthLevel: number | null
  /** Cheapest fresh listing within the requested scope (home DC or region). */
  cheapestBuy: number
  /** True when the cheaper source listing is on the HQ board. */
  hqSource: boolean
  expectedNet: number
  evGross: number
  evPerHour: number
  expectedTimeOnShelfHours: number
  /**
   * Share of the desynth components that had a sufficient sell model.
   * Below 1 the EV is a lower bound — some components earn gil the
   * model can't see yet.
   */
  pricedComponentFraction: number
  /** EV/hour of plain resale at the same buy price; null without a home model. */
  resaleEvPerHour: number | null
}

export interface ListParams {
  homeWorld?: number
  limit?: number
  /** Empty or omitted = every class the caller qualifies for. */
  classes?: string[]
  /** 0 or omitted = no lower bound. */
  minLevel?: number
  /** Buy-probe scope; omitted = home data center. */
  scope?: 'dc' | 'region'
}

export async function list(params: ListParams = {}): Promise<DesynthCandidate[]> {
  const q: Record<string, string> = {}
  if (params.homeWorld != null) q.homeWorld = String(params.homeWorld)
  if (params.limit != null) q.limit = String(params.limit)
  if (params.classes && params.classes.length > 0) q.class = params.classes.join(',')
  if (params.minLevel != null && params.minLevel > 0) q.minLevel = String(params.minLevel)
  if (params.scope === 'region') q.scope = 'region'
  const { data } = await apiClient.get<DesynthCandidate[]>('/desynth', { params: q })
  return data
}
