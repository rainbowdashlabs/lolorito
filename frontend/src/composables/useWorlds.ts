/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref } from 'vue'
import { worldsApi } from '@/api'
import type { RegionDto, WorldDto } from '@/api/worlds'

const regions = ref<RegionDto[]>([])
const loaded = ref(false)
let inflight: Promise<void> | null = null

async function load(): Promise<void> {
  if (loaded.value) return
  if (inflight) return inflight
  inflight = worldsApi.list().then((r) => {
    regions.value = r
    loaded.value = true
    inflight = null
  })
  return inflight
}

function worldById(id: number): { world: WorldDto | null; dataCenter: string | null; region: string | null } {
  for (const region of regions.value) {
    for (const dc of region.dataCenters) {
      for (const w of dc.worlds) {
        if (w.id === id) return { world: w, dataCenter: dc.name, region: region.name }
      }
    }
  }
  return { world: null, dataCenter: null, region: null }
}

export function useWorlds() {
  return { regions, loaded, load, worldById }
}
