/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import { itemTrend } from '@/api/trends'
import type { TrendRow } from '@/api/trends'

const { t } = useI18n()

const props = defineProps<{ itemId: number; hq: boolean }>()

const trend = ref<TrendRow | null>(null)
const loaded = ref(false)

async function refresh() {
  loaded.value = false
  try {
    trend.value = await itemTrend(props.itemId, { hq: props.hq })
  } catch {
    trend.value = null
  } finally {
    loaded.value = true
  }
}

onMounted(refresh)
watch(() => [props.itemId, props.hq], refresh)
</script>

<template>
  <div v-if="loaded && trend" class="flex flex-wrap items-center gap-x-5 gap-y-1 text-sm">
    <span
      class="inline-flex items-center gap-1 font-semibold"
      :class="trend.slope >= 0 ? 'text-(--color-success)' : 'text-(--color-error)'"
      :title="t('trends.slopeHint')"
    >
      {{ trend.slope >= 0 ? '▲' : '▼' }}
      {{ trend.slope >= 0 ? '+' : '' }}{{ trend.slope.toFixed(1) }} {{ t('trends.unitsPerDay') }}
      <span v-if="trend.avgUnitsPerDay > 0" class="font-normal text-(--text-muted)">
        ({{ trend.relativeSlope >= 0 ? '+' : '' }}{{ Math.round(trend.relativeSlope * 100) }}%/d)
      </span>
    </span>
    <span :title="t('trends.forecastHint')">
      {{ t('trends.forecast') }}: <span class="font-semibold">{{ trend.predictedNext24h }}</span>
      <MutedText size="sm" class="ml-1">{{ t('trends.today') }}: {{ trend.lastDayUnits }}</MutedText>
    </span>
    <MutedText v-if="trend.weakFit" size="sm" :title="t('trends.weakFitHint', { r2: trend.r2.toFixed(2) })">
      {{ t('trends.weakFit') }}
    </MutedText>
  </div>
  <MutedText v-else-if="loaded" size="sm">{{ t('trends.noSales') }}</MutedText>
</template>
