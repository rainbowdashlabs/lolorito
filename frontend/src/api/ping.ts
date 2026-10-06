/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface Pong {
  ok: boolean
  serverTime: string
}

export async function ping(): Promise<Pong> {
  const { data } = await apiClient.get<Pong>('/ping')
  return data
}
