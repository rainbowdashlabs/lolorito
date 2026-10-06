/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'
import type { BasketAction } from '@/composables/useBasket'

export type BasketVisibility = 'private' | 'authenticated' | 'public'

export interface BasketItemDto {
  key: string
  itemId: number
  itemName: string
  hq: boolean
  sourceWorldId: number
  sourceWorldName: string
  quantity: number
  buyPrice: number
  action: BasketAction
  evPerHour: number
  addedAt: string
}

export interface Basket {
  id: string
  name: string
  visibility: BasketVisibility
  shareToken: string
  createdAt: string
  updatedAt: string
  items: BasketItemDto[]
}

/** Shared read strips ownership fields — the SPA gets just what it can render. */
export interface SharedBasket {
  name: string
  visibility: BasketVisibility
  updatedAt: string
  items: BasketItemDto[]
}

export interface CreateRequest {
  name: string
  visibility: BasketVisibility
  items: BasketItemDto[]
}

export interface UpdateRequest {
  name?: string
  visibility?: BasketVisibility
  items?: BasketItemDto[]
}

export async function list(): Promise<Basket[]> {
  const { data } = await apiClient.get<Basket[]>('/baskets')
  return data
}

export async function create(req: CreateRequest): Promise<Basket> {
  const { data } = await apiClient.post<Basket>('/baskets', req)
  return data
}

export async function get(id: string): Promise<Basket> {
  const { data } = await apiClient.get<Basket>(`/baskets/${id}`)
  return data
}

export async function update(id: string, req: UpdateRequest): Promise<Basket> {
  const { data } = await apiClient.put<Basket>(`/baskets/${id}`, req)
  return data
}

export async function remove(id: string): Promise<void> {
  await apiClient.delete(`/baskets/${id}`)
}

export async function getShared(token: string): Promise<SharedBasket> {
  const { data } = await apiClient.get<SharedBasket>(`/baskets/shared/${token}`)
  return data
}
