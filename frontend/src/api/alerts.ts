/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export type AlertKind = 'price_below' | 'price_above'

export interface AlertRule {
  id: string
  itemId: number
  worldId: number | null
  dataCenterId: number | null
  hq: boolean | null
  kind: AlertKind
  thresholdPrice: number
  enabled: boolean
  cooldownMinutes: number
  lastTriggeredAt: string | null
  createdAt: string
}

export interface CreateRequest {
  itemId: number
  worldId?: number | null
  dataCenterId?: number | null
  hq?: boolean | null
  kind: AlertKind
  thresholdPrice: number
  cooldownMinutes?: number
}

export async function list(): Promise<AlertRule[]> {
  const { data } = await apiClient.get<AlertRule[]>('/alerts')
  return data
}

export async function create(req: CreateRequest): Promise<AlertRule> {
  const { data } = await apiClient.post<AlertRule>('/alerts', req)
  return data
}

export async function remove(id: string): Promise<void> {
  await apiClient.delete(`/alerts/${id}`)
}

export async function setEnabled(id: string, enabled: boolean): Promise<AlertRule> {
  const { data } = await apiClient.patch<AlertRule>(`/alerts/${id}`, { enabled })
  return data
}

/** Fire this rule immediately (owner only). Dispatches through the same sinks as a real trigger. */
export async function test(id: string): Promise<void> {
  await apiClient.post(`/alerts/${id}/test`)
}
