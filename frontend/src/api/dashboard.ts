/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface CalibrationSnapshot {
  windowDays: number
  sampleCount: number
  logRatioMean: number
  logRatioSigma: number
  interpretation: string
}

export interface CalibrationHistoryPoint {
  capturedAt: string
  sampleCount: number
  logRatioMean: number
  logRatioSigma: number
}

export interface PerfPoint {
  capturedAt: string
  valueMs: number
  detail: string | null
}

export interface CapabilityStats {
  totalModels: number
  sufficientModels: number
  uniqueItems: number
  uniqueWorlds: number
  totalListings: number
  totalSales: number
  enabledAlerts: number
  alertsFired24h: number
  lastRefitAt: string | null
  databaseBytes: number
  modelRefitMs: PerfPoint[]
  viewRefreshMs: PerfPoint[]
}

export async function calibration(windowDays?: number): Promise<CalibrationSnapshot> {
  const params: Record<string, string> = {}
  if (windowDays != null) params.window = String(windowDays)
  const { data } = await apiClient.get<CalibrationSnapshot>('/calibration', { params })
  return data
}

export async function calibrationHistory(hours = 24 * 7): Promise<CalibrationHistoryPoint[]> {
  const { data } = await apiClient.get<CalibrationHistoryPoint[]>('/calibration/history', {
    params: { hours: String(hours) },
  })
  return data
}

export async function stats(): Promise<CapabilityStats> {
  const { data } = await apiClient.get<CapabilityStats>('/dashboard/stats')
  return data
}
