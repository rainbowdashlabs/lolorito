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

function patchDebounced(update: FilterPatch, delayMs = 400): void {
  pendingPatch = { ...pendingPatch, ...update }
  if (pending) clearTimeout(pending)
  pending = setTimeout(async () => {
    const toSend = pendingPatch
    pendingPatch = {}
    pending = null
    await patch(toSend)
  }, delayMs)
}

/** Overwrite the cached filter row without hitting the server. */
function setLocal(row: FilterRow): void {
  current.value = row
}

export function useFilter() {
  return { current, loading, load, patch, patchDebounced, setLocal }
}
