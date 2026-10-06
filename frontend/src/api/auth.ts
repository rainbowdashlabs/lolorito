/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient, rootClient } from '@/api/client'

export interface MeResponse {
  id: string
  username: string
  displayName: string
  avatarUrl: string | null
  locale: string | null
}

export async function me(): Promise<MeResponse | null> {
  try {
    const { data } = await apiClient.get<MeResponse>('/me')
    return data
  } catch (e: unknown) {
    if (isAxiosStatus(e, 401)) return null
    throw e
  }
}

export async function logout(): Promise<void> {
  await rootClient.post('auth/logout')
}

export async function setLocale(locale: string): Promise<void> {
  await apiClient.put('/me/locale', { locale })
}

/** Load the caller's saved planner form. Returns null when nothing is saved yet. */
export async function loadPlannerParams<T>(): Promise<T | null> {
  try {
    const { data } = await apiClient.get<T | null>('/me/planner-params')
    return data ?? null
  } catch (e: unknown) {
    if (isAxiosStatus(e, 401)) return null
    return null
  }
}

/** Persist the caller's current planner form so the next visit restores it. */
export async function savePlannerParams(params: unknown): Promise<void> {
  try {
    await apiClient.put('/me/planner-params', params)
  } catch (e: unknown) {
    if (isAxiosStatus(e, 401)) return
    throw e
  }
}

export function loginRedirectUrl(): string {
  return '/auth/login'
}

function isAxiosStatus(e: unknown, status: number): boolean {
  const obj = e as { response?: { status?: number } }
  return obj?.response?.status === status
}
