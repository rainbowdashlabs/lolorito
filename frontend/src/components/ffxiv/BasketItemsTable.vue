/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import THead from '@/components/table/THead.vue'
import Th from '@/components/table/Th.vue'
import TRow from '@/components/table/TRow.vue'
import Td from '@/components/table/Td.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import WorldBadge from '@/components/ffxiv/WorldBadge.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NumberInput from '@/components/input/text/NumberInput.vue'
import type { BasketItem } from '@/composables/useBasket'

const { t } = useI18n()

defineProps<{ items: BasketItem[] }>()
defineEmits<{
  updateQty: [key: string, value: string | number | undefined | null]
  remove: [key: string]
}>()

function lineTotal(qty: number, price: number): number {
  return qty * price
}
</script>

<template>
  <NeutralContainer class="-mx-4 overflow-x-auto px-4 sm:mx-0 sm:px-0">
    <table class="w-full min-w-[720px] text-sm">
      <THead>
        <Th>{{ t('basket.col.item') }}</Th>
        <Th>{{ t('basket.col.from') }}</Th>
        <Th align="right">{{ t('basket.col.qty') }}</Th>
        <Th align="right">{{ t('basket.col.buy') }}</Th>
        <Th align="right">{{ t('basket.col.line') }}</Th>
        <Th>{{ t('basket.col.action') }}</Th>
        <Th />
      </THead>
      <tbody>
        <TRow v-for="row in items" :key="row.key">
          <Td>
            <span class="inline-flex items-center gap-2">
              <ItemIcon :item-id="row.itemId" :size="24" :alt="row.itemName" />
              <span class="font-medium">{{ row.itemName }}</span>
              <HQMark :hq="row.hq" />
            </span>
          </Td>
          <Td><WorldBadge :world-name="row.sourceWorldName" /></Td>
          <Td align="right" class="w-24">
            <NumberInput
              :model-value="row.quantity"
              :min="1"
              :step="1"
              class="w-20 text-right"
              @update:model-value="$emit('updateQty', row.key, $event)"
            />
          </Td>
          <Td align="right"><GilAmount :value="row.buyPrice" /></Td>
          <Td align="right" class="font-medium">
            <GilAmount :value="lineTotal(row.quantity, row.buyPrice)" />
          </Td>
          <Td><MutedText size="sm" class="capitalize">{{ row.action }}</MutedText></Td>
          <Td align="right"><DeleteButton @click="$emit('remove', row.key)" /></Td>
        </TRow>
      </tbody>
    </table>
  </NeutralContainer>
</template>
