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
import THead from '@/components/table/THead.vue'
import Th from '@/components/table/Th.vue'
import TRow from '@/components/table/TRow.vue'
import Td from '@/components/table/Td.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import type { Plan } from '@/api/planner'
import { recommendationFor, type SellRecommendation } from '@/composables/useSellRecommendation'

const { t } = useI18n()
const props = defineProps<{ plan: Plan }>()

const rows = computed<SellRecommendation[]>(() => {
  const out: SellRecommendation[] = []
  const push = (rec: SellRecommendation | null) => {
    if (rec) out.push(rec)
  }
  props.plan.stops.forEach((stop) => stop.buys.forEach((b) => push(recommendationFor(b))))
  props.plan.retainerBasket.forEach((r) => push(recommendationFor(r)))
  return out.sort((a, b) => b.expectedNet - a.expectedNet)
})

const usedListings = computed(() => rows.value.reduce((n, r) => n + r.stacks, 0))
</script>

<template>
  <InfoContainer class="mt-3 print:break-inside-avoid">
    <SectionHeader>{{ t('planner.readyToList') }}</SectionHeader>
    <MutedText size="sm">
      {{ t('planner.readyAllHint', { used: usedListings, total: plan.retainerSlots || plan.retainerBasket.length }) }}
    </MutedText>
    <table v-if="rows.length > 0" class="mt-3 w-full text-sm">
      <THead>
        <Th>{{ t('offers.col.item') }}</Th>
        <Th align="right">{{ t('planner.units') }}</Th>
        <Th align="right">{{ t('planner.stackCol') }}</Th>
        <Th align="right">{{ t('planner.listPrice') }}</Th>
      </THead>
      <tbody>
        <TRow v-for="r in rows" :key="`${r.itemId}-${r.hq ? 'hq' : 'nq'}`">
          <Td>
            <div class="flex items-center gap-2">
              <span>{{ r.itemName }}</span>
              <HQMark :hq="r.hq" />
            </div>
            <MutedText size="sm">
              {{ t('planner.stacksOf', { stacks: r.stacks, size: r.suggestedStackSize }, r.suggestedStackSize) }}
            </MutedText>
          </Td>
          <Td align="right">{{ r.qty }}</Td>
          <Td align="right">{{ r.suggestedStackSize }}</Td>
          <Td align="right"><GilAmount :value="r.listAt" /></Td>
        </TRow>
      </tbody>
    </table>
    <div v-else class="mt-3">
      <MutedText size="sm">{{ t('planner.nothingToSell') }}</MutedText>
    </div>
  </InfoContainer>
</template>
