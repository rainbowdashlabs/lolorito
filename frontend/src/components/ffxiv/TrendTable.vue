/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import THead from '@/components/table/THead.vue'
import Th from '@/components/table/Th.vue'
import TRow from '@/components/table/TRow.vue'
import Td from '@/components/table/Td.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import type { TrendRow } from '@/api/trends'

const { t } = useI18n()

defineProps<{ rows: TrendRow[] }>()

function slopeLabel(row: TrendRow): string {
  const sign = row.slope >= 0 ? '+' : ''
  return `${sign}${row.slope.toFixed(1)}/d`
}
</script>

<template>
  <div v-if="rows.length === 0">
    <MutedText size="sm">{{ t('trends.empty') }}</MutedText>
  </div>
  <table v-else class="w-full text-sm">
    <THead>
      <Th class="uppercase text-xs tracking-wider text-(--text-muted)">{{ t('offers.col.item') }}</Th>
      <Th align="right" class="uppercase text-xs tracking-wider text-(--text-muted)">
        <span :title="t('trends.slopeHint')" class="border-b border-dotted border-(--text-muted)">
          {{ t('trends.slope') }}
        </span>
      </Th>
      <Th align="right" class="uppercase text-xs tracking-wider text-(--text-muted)">{{ t('trends.today') }}</Th>
      <Th align="right" class="uppercase text-xs tracking-wider text-(--text-muted)">
        <span :title="t('trends.forecastHint')" class="border-b border-dotted border-(--text-muted)">
          {{ t('trends.forecast') }}
        </span>
      </Th>
    </THead>
    <tbody>
      <TRow v-for="row in rows" :key="`${row.itemId}-${row.hq}`">
        <Td>
          <router-link
            :to="{ name: 'item', params: { id: String(row.itemId) } }"
            class="inline-flex items-center gap-2 hover:text-(--color-primary-accent) hover:underline"
          >
            <ItemIcon :item-id="row.itemId" :size="20" :alt="row.itemName" />
            <span>{{ row.itemName }}</span>
            <HQMark :hq="row.hq" />
            <span
              v-if="row.weakFit"
              class="text-xs text-(--text-muted)"
              :title="t('trends.weakFitHint', { r2: row.r2.toFixed(2) })"
            >
              ~
            </span>
          </router-link>
        </Td>
        <Td align="right">
          <span
            :class="row.slope >= 0 ? 'text-(--color-success)' : 'text-(--color-error)'"
            :title="`${(row.relativeSlope * 100).toFixed(0)}% of avg volume per day · r² ${row.r2.toFixed(2)}`"
          >
            {{ slopeLabel(row) }}
          </span>
        </Td>
        <Td align="right" class="text-(--text-muted)">{{ row.lastDayUnits }}</Td>
        <Td align="right">
          <span class="font-semibold">{{ row.predictedNext24h }}</span>
        </Td>
      </TRow>
    </tbody>
  </table>
</template>
