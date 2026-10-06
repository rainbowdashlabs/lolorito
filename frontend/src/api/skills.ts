/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export type SkillKind = 'craft' | 'desynth'

export type SkillMap = Record<SkillKind, Record<string, number>>

/** Canonical class names in the order the SPA renders them. */
export const CANONICAL_CLASSES = [
  'carpenter',
  'blacksmith',
  'armorer',
  'goldsmith',
  'leatherworker',
  'weaver',
  'alchemist',
  'culinarian',
] as const

export interface SkillLimits {
  /** Display hints only — nothing is enforced; caps rise with expansions. */
  craftMax: number
  /** Highest item level in the catalog — what desynth skill tracks. */
  desynthMax: number
}

export async function limits(): Promise<SkillLimits> {
  const { data } = await apiClient.get<SkillLimits>('/skills/limits')
  return data
}

export async function list(): Promise<SkillMap> {
  const { data } = await apiClient.get<SkillMap>('/me/skills')
  return data
}

export async function put(kind: SkillKind, className: string, level: number): Promise<SkillMap> {
  const { data } = await apiClient.put<SkillMap>('/me/skills', { kind, className, level })
  return data
}
