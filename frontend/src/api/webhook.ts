/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export async function get(): Promise<string> {
  const { data } = await apiClient.get<{ url: string }>('/me/alert-webhook')
  return data.url
}

export async function put(url: string): Promise<string> {
  const { data } = await apiClient.put<{ url: string }>('/me/alert-webhook', { url })
  return data.url
}
