/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref } from 'vue'

export type BasketAction = 'resale' | 'desynth' | 'craft'

export interface BasketItem {
  key: string          // stable id: `${sourceWorldId}-${itemId}-${hq ? 'hq' : 'nq'}-${action}`
  itemId: number
  itemName: string
  hq: boolean
  sourceWorldId: number
  sourceWorldName: string
  quantity: number
  buyPrice: number
  action: BasketAction
  evPerHour: number    // snapshot at time of add
  addedAt: string      // ISO
}

const STORAGE_KEY = 'lolorito_basket_v1'

function loadFromStorage(): BasketItem[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function persist(items: BasketItem[]) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
  } catch {
    // Quota exceeded / private mode — silent; the in-memory basket still works.
  }
}

const items = ref<BasketItem[]>(loadFromStorage())

function add(item: Omit<BasketItem, 'key' | 'addedAt'>) {
  const key = `${item.sourceWorldId}-${item.itemId}-${item.hq ? 'hq' : 'nq'}-${item.action}`
  const existing = items.value.find((b) => b.key === key)
  if (existing) {
    existing.quantity += item.quantity
    persist(items.value)
    return
  }
  items.value.push({
    ...item,
    key,
    addedAt: new Date().toISOString(),
  })
  persist(items.value)
}

function remove(key: string) {
  items.value = items.value.filter((b) => b.key !== key)
  persist(items.value)
}

/**
 * Replace the quantity on a basket row. Used by the full-page basket
 * view for per-row editing — `add()` sums into the existing row, which
 * is the wrong shape for a direct edit.
 */
function setQuantity(key: string, quantity: number) {
  const clamped = Math.max(1, Math.floor(quantity))
  const row = items.value.find((b) => b.key === key)
  if (!row) return
  row.quantity = clamped
  persist(items.value)
}

function clear() {
  items.value = []
  persist(items.value)
}

export function useBasket() {
  return {
    items,
    count: computed(() => items.value.length),
    totalSpend: computed(() => items.value.reduce((sum, b) => sum + b.quantity * b.buyPrice, 0)),
    totalEvPerHour: computed(() => items.value.reduce((sum, b) => sum + b.evPerHour, 0)),
    add,
    remove,
    setQuantity,
    clear,
  }
}
