/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import OffersTable from '@/components/ffxiv/OffersTable.vue'
import OffersFilterPanel from '@/components/ffxiv/OffersFilterPanel.vue'
import type { OffersClientFilter } from '@/components/ffxiv/OffersFilterPanel.vue'
import { offersApi } from '@/api'
import type { ScoredOffer } from '@/api/offers'
import { useFilter } from '@/composables/useFilter'
import { useWorlds } from '@/composables/useWorlds'
import { useValuationRefresh } from '@/composables/useValuationRefresh'

const { t } = useI18n()
const router = useRouter()
const filter = useFilter()
const worlds = useWorlds()
const offers = ref<ScoredOffer[]>([])
const homeWorldName = ref<string>('')
const dataCenter = ref<string>('')
const loading = ref(false)
const error = ref<string | null>(null)

/**
 * Client-side filter state — layered on top of the raw offers list.
 * Persisted only in session memory; the home world / freshness bits
 * flow into the saved filter row on the backend.
 */
const clientFilter = ref<OffersClientFilter>({
  minEvPerHour: 0,
  minShelf: 'any',
  hideBot: false,
  hideGhost: false,
  hideInsufficient: false,
})

/**
 * Search scope lives on the persisted filter row (`target` column).
 * Fall back to DATA_CENTER when the row hasn't loaded yet so the
 * select never renders a null option — the effective scope is
 * whatever the backend returns.
 */
const scope = computed<'DATA_CENTER' | 'REGION'>(() => {
  const t = filter.current.value?.target
  return t === 'REGION' ? 'REGION' : 'DATA_CENTER'
})

/**
 * The row list filtered by the client-side controls. Backend still
 * returns everything on the DC; this trims to what the user chose to see.
 *
 * <p>Shelf horizon behaviour: when the user asks for offers that clear
 * within X hours and an offer's full quantity would take longer,
 * project the fraction that fits in the horizon and re-scale the
 * valuation linearly (uniform sale rate assumption). A 1400-unit
 * offer with a 48h clear time still shows on "< 1 day" as 700 units
 * with half the projected EV — hiding it entirely wastes the fact
 * that half of it does move in the window.
 */
const shownOffers = computed(() => {
  const cf = clientFilter.value
  const horizon = shelfHorizonHours(cf.minShelf)
  const out: ScoredOffer[] = []
  for (const o of offers.value) {
    if (o.valuation.evPerHour < cf.minEvPerHour) continue
    if (cf.hideBot && o.lambdaUndercut > 0.05) continue
    if (cf.hideGhost && o.ghostFraction > 0.1) continue
    if (cf.hideInsufficient && !o.modelSufficient) continue
    if (cf.minShelf === 'overnight-ok' && o.valuation.expectedTimeOnShelfHours < 8) continue

    if (horizon != null && o.valuation.expectedTimeOnShelfHours > horizon) {
      const frac = horizon / o.valuation.expectedTimeOnShelfHours
      const partialQty = Math.floor(o.quantity * frac)
      if (partialQty <= 0) continue
      // Fully-partial fill: qty, gross EV, and expected profit all scale
      // linearly with the horizon fraction; EV/hour holds because both
      // numerator (profit) and denominator (attention hours) shrink by
      // the same factor.
      out.push({
        ...o,
        quantity: partialQty,
        originalQuantity: o.quantity,
        valuation: {
          ...o.valuation,
          expectedTimeOnShelfHours: horizon,
          expectedNet: o.valuation.expectedNet * frac,
          evGross: o.valuation.evGross * frac,
        },
      })
    } else {
      out.push(o)
    }
  }
  return out
})

/**
 * Horizon in hours implied by the shelf class filter, or {@code null}
 * when there's no cap (any / overnight-ok both admit long shelves).
 */
function shelfHorizonHours(shelf: OffersClientFilter['minShelf']): number | null {
  switch (shelf) {
    case 'lt-1h':
      return 1
    case 'lt-1d':
      return 24
    default:
      return null
  }
}

const homeWorldOptions = computed(() => {
  const list: { id: number; label: string }[] = []
  for (const r of worlds.regions.value) {
    for (const dc of r.dataCenters) {
      for (const w of dc.worlds) list.push({ id: w.id, label: `${w.name} · ${dc.name}` })
    }
  }
  return list.sort((a, b) => a.label.localeCompare(b.label))
})

async function refresh() {
  loading.value = true
  error.value = null
  try {
    const res = await offersApi.list({})
    offers.value = res.offers
    homeWorldName.value = res.homeWorld
    dataCenter.value = res.dataCenter
  } catch (e: unknown) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

function extractError(e: unknown): string {
  const obj = e as { response?: { data?: { error?: string } }; message?: string }
  return obj?.response?.data?.error ?? obj?.message ?? String(e)
}

function openItem(row: ScoredOffer) {
  router.push({ name: 'item', params: { id: String(row.itemId) }, query: { hq: row.hq ? '1' : '0' } })
}

async function onWorldChange(v: string | number | null | undefined) {
  if (v == null) return
  await filter.patch({ worldId: Number(v) })
  await refresh()
}

async function onRefreshHoursChange(v: string | number | null | undefined) {
  if (v == null) return
  await filter.patch({ refreshHours: Number(v) })
  await refresh()
}

let boundsDebounce: ReturnType<typeof setTimeout> | null = null
function onBoundsChange(bounds: { budget?: number; inventorySlots?: number }) {
  if (boundsDebounce) clearTimeout(boundsDebounce)
  boundsDebounce = setTimeout(async () => {
    await filter.patch(bounds)
    await refresh()
  }, 600)
}

async function onScopeChange(v: 'DATA_CENTER' | 'REGION') {
  await filter.patch({ target: v })
  await refresh()
}

onMounted(async () => {
  await Promise.all([filter.load(), worlds.load()])
  await refresh()
})

watch(() => filter.current.value?.worldId, () => {
  if (offers.value.length === 0 && !loading.value) refresh()
})

/**
 * Refit-driven refresh. Only fires when the refit is for an item we're
 * already showing — a refit on some far-away item shouldn't force a
 * refetch. Debounced so a broad refit sweep doesn't hammer the endpoint.
 */
useValuationRefresh(
  (evt) => {
    const homeWorldId = filter.current.value?.worldId
    if (!homeWorldId) return false
    // Home-world refits change offer rankings directly; source-world
    // refits change the buy prices we route through, so both count.
    const affectsHome = evt.worldId === homeWorldId
    if (!affectsHome && !offers.value.some((o) => o.itemId === evt.itemId)) return false
    return true
  },
  () => refresh(),
)
</script>

<template>
  <PageHeader
    :title="t('offers.title')"
    :subtitle="t('offers.subtitle', { where: `${homeWorldName || t('offers.noWorldSet')}${dataCenter ? ` · ${dataCenter}` : ''}` })"
  />

  <div class="grid gap-6 md:grid-cols-[240px_1fr]">
    <aside>
      <OffersFilterPanel
        v-model:client-filter="clientFilter"
        :home-world-id="filter.current.value?.worldId ?? null"
        :refresh-hours="filter.current.value?.refreshHours ?? 6"
        :scope="scope"
        :home-world-options="homeWorldOptions"
        :budget="filter.current.value?.budget ?? 0"
        :inventory-slots="filter.current.value?.inventorySlots ?? 0"
        @bounds-change="onBoundsChange"
        @world-change="onWorldChange"
        @refresh-hours-change="onRefreshHoursChange"
        @scope-change="onScopeChange"
      />
    </aside>

    <section>
      <div v-if="loading" class="py-8 text-center">
        <Spinner size="lg" />
        <div class="mt-2"><MutedText size="sm">{{ $t('offers.scoringRegion') }}</MutedText></div>
      </div>
      <Alert v-else-if="error" variant="error">
        {{ $t('offers.failedToLoad', { error }) }}
      </Alert>
      <EmptyState v-else-if="offers.length === 0">
        {{ $t('offers.empty') }}
      </EmptyState>
      <div v-else>
        <div class="mb-3 rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) p-3 text-sm">
          <div class="grid gap-2 md:grid-cols-3">
            <div>
              <span class="font-semibold">{{ $t('offers.col.expectedNet') }}</span>:
              <span class="text-(--text-muted)">{{ $t('offers.col.expectedNetHint') }}</span>
            </div>
            <div>
              <span class="font-semibold">{{ $t('offers.col.expectedShelf') }}</span>:
              <span class="text-(--text-muted)">{{ $t('offers.col.expectedShelfHint') }}</span>
            </div>
            <div>
              <span class="font-semibold">{{ $t('offers.col.gilPerHour') }}</span>:
              <span class="text-(--text-muted)">{{ $t('offers.col.gilPerHourHint') }}</span>
            </div>
          </div>
        </div>
        <OffersTable
          :offers="shownOffers"
          :home-world-id="filter.current.value?.worldId ?? null"
          @open="openItem"
        />
      </div>
    </section>
  </div>
</template>
