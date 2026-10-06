/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref } from 'vue'
import { filterApi } from '@/api'
import type { FilterPatch, FilterRow } from '@/api/filter'

const current = ref<FilterRow | null>(null)
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    current.value = await filterApi.get()
  } finally {
    loading.value = false
  }
}

async function patch(update: FilterPatch): Promise<void> {
  current.value = await filterApi.put(update)
}

let pending: ReturnType<typeof setTimeout> | null = null
let pendingPatch: FilterPatch = {}
let waiters: { resolve: () => void; reject: (e: unknown) => void }[] = []

/**
 * Merge `update` into the next save and send it once input has been quiet
 * for `delayMs`. The returned promise settles when that batched save does,
 * so callers can refresh afterwards.
 */
function patchDebounced(update: FilterPatch, delayMs = 400): Promise<void> {
  pendingPatch = { ...pendingPatch, ...update }
  if (pending) clearTimeout(pending)
  const settled = new Promise<void>((resolve, reject) => waiters.push({ resolve, reject }))
  pending = setTimeout(async () => {
    const toSend = pendingPatch
    const batch = waiters
    pendingPatch = {}
    waiters = []
    pending = null
    try {
      await patch(toSend)
      batch.forEach((w) => w.resolve())
    } catch (e: unknown) {
      batch.forEach((w) => w.reject(e))
    }
  }, delayMs)
  return settled
}

/** Overwrite the cached filter row without hitting the server. */
function setLocal(row: FilterRow): void {
  current.value = row
}

export function useFilter() {
  return { current, loading, load, patch, patchDebounced, setLocal }
}
