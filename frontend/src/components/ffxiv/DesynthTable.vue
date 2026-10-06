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
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import type { DesynthCandidate } from '@/api/desynth'
import { formatHours } from '@/util/format'

const { t } = useI18n()

defineProps<{ items: DesynthCandidate[] }>()
defineEmits<{ open: [row: DesynthCandidate] }>()
</script>

<template>
  <table class="w-full text-sm">
    <THead>
      <Th>{{ t('offers.col.item') }}</Th>
      <Th>{{ t('desynth.class') }}</Th>
      <Th align="right">{{ t('desynth.cheapestBuy') }}</Th>
      <Th align="right">{{ t('offers.col.expectedNet') }}</Th>
      <Th align="right">{{ t('offers.col.expectedShelf') }}</Th>
      <Th align="right">{{ t('offers.col.gilPerHour') }}</Th>
      <Th align="right">
        <span :title="t('desynth.vsResaleHint')" class="border-b border-dotted border-(--text-muted)">
          {{ t('desynth.vsResale') }}
        </span>
      </Th>
    </THead>
    <tbody>
      <TRow
        v-for="row in items"
        :key="row.itemId"
        class="cursor-pointer hover:bg-(--bg-accent)"
        @click="$emit('open', row)"
      >
        <Td>
          <span class="inline-flex items-center gap-2">
            <ItemIcon :item-id="row.itemId" :size="24" :alt="row.itemName" />
            <span class="font-medium">{{ row.itemName }}</span>
          </span>
        </Td>
        <Td>
          <span v-if="row.desynthClass" class="capitalize">
            {{ row.desynthClass }}<span v-if="row.desynthLevel" class="text-(--text-muted)"> · L{{ row.desynthLevel }}</span>
          </span>
          <MutedText v-else size="sm">—</MutedText>
        </Td>
        <Td align="right"><GilAmount :value="row.cheapestBuy" /><HQMark :hq="row.hqSource" /></Td>
        <Td align="right">
          <span
            v-if="row.pricedComponentFraction < 1"
            :title="t('desynth.partialPricingHint', { pct: Math.round(row.pricedComponentFraction * 100) })"
          >
            <span class="mr-1 text-xs text-(--text-muted)">≥</span>
            <GilAmount :value="Math.round(row.expectedNet)" />
          </span>
          <GilAmount v-else :value="Math.round(row.expectedNet)" /></Td>
        <Td align="right">{{ formatHours(row.expectedTimeOnShelfHours) }}</Td>
        <Td align="right">
          <span class="font-semibold text-(--color-success)">
            <GilAmount :value="Math.round(row.evPerHour)" />
          </span>
        </Td>
        <Td align="right">
          <template v-if="row.resaleEvPerHour != null">
            <span
              :class="row.evPerHour > row.resaleEvPerHour ? 'text-(--color-success)' : 'text-(--color-error)'"
              :title="t('desynth.plainResaleHint', { value: Math.round(row.resaleEvPerHour).toLocaleString() })"
            >
              {{ row.evPerHour > row.resaleEvPerHour ? '+' : '' }}{{ Math.round(row.evPerHour - row.resaleEvPerHour).toLocaleString() }}/h
            </span>
          </template>
          <MutedText v-else size="sm">—</MutedText>
        </Td>
      </TRow>
    </tbody>
  </table>
</template>
