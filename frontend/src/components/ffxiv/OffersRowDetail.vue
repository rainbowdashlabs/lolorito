/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { itemsApi } from '@/api'
import type { ItemDetailDto, ItemListingDto } from '@/api/items'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'

/**
 * Lazy-loaded per-row expand for the offers table. Fetches the full
 * item detail on mount (so the parent table doesn't pay for it until
 * the user opens a row) and renders two compact stacks: listings on
 * the source world we're routing through, and listings on the home
 * world we'd sell into. That's the row-expand idea distilled to what
 * the current backend already returns; race breakdown + price
 * sparkline can layer in later without changing this component's
 * contract.
 */
const props = defineProps<{
  itemId: number
  hq: boolean
  sourceWorldId: number
  homeWorldId: number
}>()

const { t } = useI18n()
const detail = ref<ItemDetailDto | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  loading.value = true
  try {
    detail.value = await itemsApi.detail(props.itemId, props.hq)
  } catch (e: unknown) {
    const obj = e as { message?: string }
    error.value = obj?.message ?? t('offers.detailLoadFailed')
  } finally {
    loading.value = false
  }
})

function topN(rows: ItemListingDto[], worldId: number, n = 5): ItemListingDto[] {
  return rows
      .filter((r) => r.worldId === worldId)
      .slice()
      .sort((a, b) => a.unitPrice - b.unitPrice)
      .slice(0, n)
}

function relative(iso: string): string {
  const ts = Date.parse(iso)
  if (!Number.isFinite(ts)) return ''
  const min = Math.max(0, Math.round((Date.now() - ts) / 60000))
  if (min < 60) return t('offers.minutesAgo', { n: min })
  const hrs = Math.round(min / 60)
  if (hrs < 48) return t('offers.hoursAgo', { n: hrs })
  return t('offers.daysAgo', { n: Math.round(hrs / 24) })
}
</script>

<template>
  <div class="p-3">
    <div v-if="loading" class="flex justify-center py-2"><Spinner /></div>
    <div v-else-if="error" class="text-sm text-(--color-error)">{{ error }}</div>
    <div v-else-if="detail" class="grid gap-4 md:grid-cols-2">
      <div>
        <MutedText size="sm" tag="div" class="uppercase tracking-wide">{{ t('offers.sourceStack') }}</MutedText>
        <ul class="mt-1 space-y-1 text-xs">
          <li
            v-for="l in topN(detail.listings, sourceWorldId)"
            :key="`${l.worldId}-${l.unitPrice}-${l.quantity}`"
            class="flex items-center gap-2 rounded-(--radius-theme) border border-(--border) bg-(--bg) px-2 py-1"
          >
            <HQMark :hq="l.hq" />
            <GilAmount :value="l.unitPrice" />
            <span class="text-(--text-muted)">× {{ l.quantity }}</span>
            <span class="ml-auto text-(--text-muted)">{{ relative(l.reviewedAt) }}</span>
          </li>
          <li v-if="topN(detail.listings, sourceWorldId).length === 0" class="text-(--text-muted)">
            {{ t('offers.noListings') }}
          </li>
        </ul>
      </div>
      <div>
        <MutedText size="sm" tag="div" class="uppercase tracking-wide">{{ t('offers.homeStack') }}</MutedText>
        <ul class="mt-1 space-y-1 text-xs">
          <li
            v-for="l in topN(detail.listings, homeWorldId)"
            :key="`${l.worldId}-${l.unitPrice}-${l.quantity}`"
            class="flex items-center gap-2 rounded-(--radius-theme) border border-(--border) bg-(--bg) px-2 py-1"
          >
            <HQMark :hq="l.hq" />
            <GilAmount :value="l.unitPrice" />
            <span class="text-(--text-muted)">× {{ l.quantity }}</span>
            <span class="ml-auto text-(--text-muted)">{{ relative(l.reviewedAt) }}</span>
          </li>
          <li v-if="topN(detail.listings, homeWorldId).length === 0" class="text-(--text-muted)">
            {{ t('offers.noListings') }}
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>
