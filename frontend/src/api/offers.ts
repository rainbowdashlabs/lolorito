/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface Valuation {
  expectedNet: number
  sigmaNet: number
  evGross: number
  expectedTimeOnShelfHours: number
  evPerHour: number
  /** Recommended list price as a multiple of the market median. */
  listRatio: number
}

export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW'

export interface ScoredOffer {
  sourceWorldId: number
  itemId: number
  itemName: string
  hq: boolean
  quantity: number
  buyPrice: number
  /** Home-world units listed at or below the median — the queue yours join. */
  depthAhead: number
  valuation: Valuation
  /**
   * Adversary flags surfaced from the underlying market model, measured
   * from the listing-episode log (undercuts/hour + aged share of the
   * open stack). Zero until the differ has seen some traffic.
   */
  lambdaUndercut: number
  ghostFraction: number
  modelSufficient: boolean
  /** True when the home model was fitted from DC-pooled sales — lower confidence. */
  modelPooled: boolean
  /** Overall trust tier from sample count, pooling, and spread vs margin. */
  confidence: Confidence
  /**
   * Set by the client-side shelf-horizon projection: when the full
   * quantity wouldn't clear inside the requested window, `quantity`
   * is trimmed to the fraction that would, and `originalQuantity`
   * carries the pre-cap value so the UI can render "X of Y".
   */
  originalQuantity?: number
}

export interface OffersResponse {
  homeWorld: string
  dataCenter: string
  count: number
  offers: ScoredOffer[]
}

export async function list(params: {
  homeWorld?: number
  limit?: number
  refreshHours?: number
  attention?: number
} = {}): Promise<OffersResponse> {
  const q: Record<string, string> = {}
  if (params.homeWorld != null) q.home_world = String(params.homeWorld)
  if (params.limit != null) q.limit = String(params.limit)
  if (params.refreshHours != null) q.refresh_hours = String(params.refreshHours)
  if (params.attention != null) q.attention = String(params.attention)
  const { data } = await apiClient.get<OffersResponse>('/offers', { params: q })
  return data
}
