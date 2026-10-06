/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { apiClient } from '@/api/client'

interface CatalogEntry {
  icon: number
  ilvl: number
  stackSize: number
}

let itemCatalog: Record<string, CatalogEntry> | null = null
let inflight: Promise<Record<string, CatalogEntry>> | null = null

/** Load the backend-owned item catalog once and cache it in memory. */
async function loadCatalog(): Promise<Record<string, CatalogEntry>> {
  if (itemCatalog) return itemCatalog
  if (!inflight) {
    inflight = apiClient.get<Record<string, CatalogEntry>>('/item-catalog').then((res) => {
      itemCatalog = res.data ?? {}
      return itemCatalog
    })
  }
  return inflight
}

/** Kick off catalog load in the background. Idempotent; safe to call from mounted hooks. */
export function preloadItemCatalog(): void {
  void loadCatalog()
}

export function iconIdFor(itemId: number): number | null {
  if (!itemCatalog) return null
  return itemCatalog[String(itemId)]?.icon ?? null
}

/**
 * Item level lookup. Requires the catalog to be loaded first — call
 * {@link preloadItemCatalog} from the component that needs ilvl.
 * Returns null until the catalog resolves.
 */
export function itemLevelFor(itemId: number): number | null {
  if (!itemCatalog) return null
  return itemCatalog[String(itemId)]?.ilvl ?? null
}

/**
 * Icon URL served by our own backend — the PNG is fetched from XIVAPI
 * once, cached on disk, and re-served without a third-party dependency.
 * Returns null until we have loaded the catalog and confirmed the item
 * has an icon.
 */
/**
 * Icon URL served by our own backend. Always resolves — the backend
 * returns 404 for items without a known icon, at which point the
 * component's onError handler swaps to a placeholder.
 */
export function xivapiIconUrl(itemId: number): string {
  return `/api/v1/item-icon/${itemId}`
}
