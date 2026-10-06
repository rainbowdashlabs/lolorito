/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

export interface Retainer {
  discordUserId: number
  retainerName: string
  worldId: number
  /**
   * True once Universalis has observed at least one listing for this
   * retainer. Undeclared retainers stay `seen=false` forever; a
   * declared retainer sits at `seen=false` until it posts something
   * to the market.
   */
  seen: boolean
  createdAt: string
  updatedAt: string
}

export interface OwnedListing {
  worldId: number
  itemId: number
  hq: boolean
  unitPrice: number
  quantity: number
  reviewTime: string
  retainerName: string | null
}

export async function list(): Promise<Retainer[]> {
  const { data } = await apiClient.get<Retainer[]>('/me/retainers')
  return data
}

export async function add(retainerName: string, worldId: number): Promise<Retainer[]> {
  const { data } = await apiClient.post<Retainer[]>('/me/retainers', { retainerName, worldId })
  return data
}

export async function remove(retainerName: string, worldId: number): Promise<void> {
  await apiClient.delete('/me/retainers', { data: { retainerName, worldId } })
}

export async function ownedListings(): Promise<OwnedListing[]> {
  const { data } = await apiClient.get<OwnedListing[]>('/me/listings')
  return data
}

/** Distinct retainer names seen on {@code worldId} in the live listings feed, filtered by prefix substring. */
export async function suggest(worldId: number, q: string): Promise<string[]> {
  const { data } = await apiClient.get<string[]>('/retainer-suggestions', {
    params: { world: String(worldId), q },
  })
  return data
}
