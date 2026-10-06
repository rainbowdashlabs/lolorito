/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface ItemSearchHit {
  itemId: number
  name: string
}

export async function search(q: string, limit = 25): Promise<ItemSearchHit[]> {
  if (!q.trim()) return []
  const { data } = await apiClient.get<ItemSearchHit[]>('/item-search', { params: { q, limit } })
  return data
}

/** Batched id → display-name lookup. */
export async function namesByIds(ids: number[]): Promise<Record<string, string>> {
  const filtered = ids.filter((n) => Number.isFinite(n) && n > 0)
  if (filtered.length === 0) return {}
  const { data } = await apiClient.get<Record<string, string>>('/item-names', {
    params: { ids: filtered.join(',') },
  })
  return data
}
