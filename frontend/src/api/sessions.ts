/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface SessionRow {
  id: string
  createdAt: string
  expiresAt: string
  userAgent: string
  current: boolean
}

export async function list(): Promise<SessionRow[]> {
  const { data } = await apiClient.get<SessionRow[]>('/me/sessions')
  return data
}

export async function revoke(id: string): Promise<void> {
  await apiClient.delete(`/me/sessions/${id}`)
}

export async function revokeAll(): Promise<{ removed: number }> {
  const { data } = await apiClient.delete<{ removed: number }>('/me/sessions')
  return data
}
