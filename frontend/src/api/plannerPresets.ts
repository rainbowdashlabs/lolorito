/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface PlannerPresetDto {
  id: string
  discordUserId: number
  name: string
  paramsJson: string
  createdAt: string
  updatedAt: string
}

export async function list(): Promise<PlannerPresetDto[]> {
  try {
    const { data } = await apiClient.get<PlannerPresetDto[]>('/me/planner-presets')
    return data
  } catch {
    return []
  }
}

export async function save(name: string, params: unknown): Promise<PlannerPresetDto | null> {
  try {
    const { data } = await apiClient.post<PlannerPresetDto>('/me/planner-presets', {
      name,
      params: JSON.stringify(params),
    })
    return data
  } catch {
    return null
  }
}

export async function rename(id: string, name: string): Promise<PlannerPresetDto | null> {
  try {
    const { data } = await apiClient.put<PlannerPresetDto>(`/me/planner-presets/${id}`, { name })
    return data
  } catch {
    return null
  }
}

export async function remove(id: string): Promise<boolean> {
  try {
    await apiClient.delete(`/me/planner-presets/${id}`)
    return true
  } catch {
    return false
  }
}
