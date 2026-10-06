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
  /** Median of per-key mean residuals — one vote per key, volume-independent. */
  keyMedianLogRatio: number
  interpretation: string
}

export interface KeyCalibration {
  itemId: number
  itemName: string
  worldId: number
  worldName: string
  hq: boolean
  count: number
  logRatioMean: number
  logRatioSigma: number
  /** exp(mean) − 1 — fractional misprediction; positive = model prices too low. */
  bias: number
}

export async function snapshot(windowDays?: number): Promise<CalibrationSnapshot> {
  const params: Record<string, string> = {}
  if (windowDays != null) params.window = String(windowDays)
  const { data } = await apiClient.get<CalibrationSnapshot>('/calibration', { params })
  return data
}

export async function worstKeys(limit = 15): Promise<KeyCalibration[]> {
  const { data } = await apiClient.get<KeyCalibration[]>('/calibration/keys', { params: { limit: String(limit) } })
  return data
}
