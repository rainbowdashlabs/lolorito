/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface CharacterProfileData {
  lodestoneId: number
  name: string
  world: string
  dataCenter: string | null
  title: string | null
  portraitUrl: string | null
  freeCompany: string | null
  activeClassIconUrl: string | null
  activeClassLevel: string | null
  jobLevels: Record<string, number>
  maxCrafterLevel: number
  maxGathererLevel: number
}

export interface CharacterEnvelope {
  lodestoneId: number
  fetchedAt: string
  stale: boolean
  profile: CharacterProfileData
}

/** Returns null when the user hasn't linked a character yet. */
export async function get(): Promise<CharacterEnvelope | null> {
  try {
    const { data } = await apiClient.get<CharacterEnvelope>('/me/character')
    return data
  } catch (e: unknown) {
    if (isNotFound(e)) return null
    throw e
  }
}

export async function refresh(lodestoneId: number): Promise<CharacterEnvelope> {
  const { data } = await apiClient.post<CharacterEnvelope>('/me/character/refresh', { lodestoneId })
  return data
}

export async function unlink(): Promise<void> {
  try {
    await apiClient.delete('/me/character')
  } catch (e: unknown) {
    if (isNotFound(e)) return
    throw e
  }
}

function isNotFound(e: unknown): boolean {
  const obj = e as { response?: { status?: number } }
  return obj?.response?.status === 404
}
