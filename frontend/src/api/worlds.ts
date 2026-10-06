/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface WorldDto { id: number; name: string }
export interface DataCenterDto { id: number; name: string; worlds: WorldDto[] }
export interface RegionDto { name: string; dataCenters: DataCenterDto[] }

export async function list(): Promise<RegionDto[]> {
  const { data } = await apiClient.get<RegionDto[]>('/worlds')
  return data
}
