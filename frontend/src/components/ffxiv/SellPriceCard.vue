/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import type { ItemDetailDto, SalesBucket } from '@/api/items'

const { t } = useI18n()
const props = defineProps<{ item: ItemDetailDto; salesBuckets?: SalesBucket[] }>()

/**
 * Cheapest current listing on the HOME WORLD only. Buyers browsing your
 * board don't see other worlds' prices, so a cheaper listing elsewhere
 * on the DC is not competition worth undercutting.
 */
const homeCheapest = computed<number | null>(() => {
  const home = props.item.listings.filter((l) => l.worldId === props.item.homeWorldId)
  if (home.length === 0) return null
  return home.reduce((min, l) => Math.min(min, l.unitPrice), Number.POSITIVE_INFINITY)
})

/**
 * Units-weighted average price across the recent sales buckets — the
 * zero-model fallback ("what did this actually sell for lately").
 */
const salesAverage = computed<number | null>(() => {
  const buckets = props.salesBuckets ?? []
  let units = 0
  let value = 0
  for (const b of buckets) {
    units += b.units
    value += b.units * b.avgPrice
  }
  return units > 0 ? Math.round(value / units) : null
})

/**
 * Pick a suggested list price:
 *  - undercut of the home world's current cheapest listing (min − 1) when it
 *    beats the model median,
 *  - the model median otherwise (or when the home board is empty),
 *  - the recent-sales average when there's no home listing AND no model,
 *  - nothing when we're blind on all three.
 */
const suggestion = computed(() => {
  const cheapest = homeCheapest.value
  const median = props.item.model?.medianPrice ?? null

  if (cheapest != null && median != null) {
    // Undercut only when the cheapest is at or above what the model expects — otherwise
    // matching the market would price us below fair value, so we hold at the median.
    if (cheapest <= median) {
      return { price: cheapest - 1, reason: 'undercut', undercutOf: cheapest }
    }
    return { price: Math.round(median), reason: 'median' }
  }
  if (cheapest != null) return { price: cheapest - 1, reason: 'undercut', undercutOf: cheapest }
  if (median != null) return { price: Math.round(median), reason: 'median' }
  if (salesAverage.value != null) return { price: salesAverage.value, reason: 'sales' }
  return null
})

const explain = computed(() => {
  if (!suggestion.value) return ''
  if (suggestion.value.reason === 'undercut') {
    return t('item.explainUndercut', {
      world: props.item.homeWorldName,
      price: suggestion.value.undercutOf?.toLocaleString(),
    })
  }
  if (suggestion.value.reason === 'sales') {
    return t('item.explainSales', { world: props.item.homeWorldName })
  }
  return t('item.explainMedian')
})
</script>

<template>
  <InfoContainer>
    <SectionHeader>{{ t('item.listFor') }}</SectionHeader>
    <div v-if="suggestion" class="mt-2 text-3xl font-semibold">
      <GilAmount :value="suggestion.price" />
    </div>
    <div v-else class="mt-2 text-lg font-semibold">
      <MutedText size="sm">{{ t('item.noPriceData') }}</MutedText>
    </div>
    <div class="mt-2">
      <MutedText size="sm">{{ explain }}</MutedText>
    </div>
    <div v-if="item.model" class="mt-3 grid grid-cols-2 gap-2 text-xs">
      <div>
        <MutedText size="sm" :title="t('item.histMeanHint')">
          {{ t('item.histMean') }}
        </MutedText>
        <div class="font-mono"><GilAmount :value="Math.round(item.model.expectedPrice)" /></div>
      </div>
      <div>
        <MutedText size="sm" :title="t('item.histMedianHint')">
          {{ t('item.histMedian') }}
        </MutedText>
        <div class="font-mono"><GilAmount :value="Math.round(item.model.medianPrice)" /></div>
      </div>
    </div>
    <div v-if="item.model && homeCheapest != null && Math.round(item.model.expectedPrice) > homeCheapest * 1.15" class="mt-3 rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) px-3 py-2 text-xs">
      <MutedText size="sm">
        {{ t('item.undercutNote', {
          world: item.homeWorldName,
          cheapest: homeCheapest.toLocaleString(),
          mean: Math.round(item.model.expectedPrice).toLocaleString(),
        }) }}
      </MutedText>
    </div>
  </InfoContainer>
</template>
