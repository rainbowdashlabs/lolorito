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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import type { Plan, PlanDesynth } from '@/api/planner'

const { t } = useI18n()
const props = defineProps<{ plan: Plan }>()

/** Expected component proceeds (net of tax) — expectedNet is per source unit. */
function returnsOf(desynth: PlanDesynth): number {
  return desynth.valuation.expectedNet * desynth.qty
}

const totalReturns = computed(() => props.plan.desynths.reduce((sum, d) => sum + returnsOf(d), 0))

function outputsLabel(desynth: PlanDesynth): string {
  return desynth.outputs
    .map((o) => `~${o.avgQty % 1 === 0 ? o.avgQty : o.avgQty.toFixed(1)}× ${o.itemName}`)
    .join(', ')
}
</script>

<template>
  <NeutralContainer v-if="plan.desynths.length > 0" class="mt-3 print:break-inside-avoid">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <SectionHeader>{{ t('planner.desynthAtHome') }}</SectionHeader>
        <MutedText size="sm">
          {{ t('planner.desynthAtHomeHint', { world: plan.homeWorldName }) }}
        </MutedText>
      </div>
      <div class="text-right text-sm">
        <MutedText size="sm">
          {{ t('planner.sourcesPrefix') }} <GilAmount :value="plan.desynthBuyCost" />
          {{ t('planner.componentsApprox') }} <GilAmount :value="Math.round(totalReturns)" />
        </MutedText>
        <div class="font-semibold text-(--color-success)">
          <GilAmount :value="Math.round(plan.desynthEvGross)" /> {{ t('planner.profitSuffix') }}
        </div>
      </div>
    </div>

    <div class="mt-3 grid gap-1 text-sm">
      <div
        v-for="desynth in plan.desynths"
        :key="desynth.uniqueKey"
        class="rounded-(--radius-theme) bg-(--bg) px-3 py-2"
      >
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <div class="flex flex-1 items-center gap-2">
            <ItemIcon :item-id="desynth.itemId" :size="24" :alt="desynth.itemName" />
            <span class="font-medium">{{ desynth.itemName }}<HQMark :hq="desynth.hq" /></span>
            <MutedText size="sm">× {{ desynth.qty }}</MutedText>
            <span
              v-if="desynth.desynthClass"
              class="rounded bg-(--bg-accent) px-1.5 py-0.5 text-xs capitalize text-(--text-muted)"
              :title="desynth.desynthVerified
                ? t('planner.desynthReqVerified', { class: desynth.desynthClass, level: desynth.desynthLevel })
                : t('planner.desynthReqUnverified', { class: desynth.desynthClass, level: desynth.desynthLevel })"
            >
              {{ desynth.desynthClass }} L{{ desynth.desynthLevel }}<span v-if="!desynth.desynthVerified"> ?</span>
            </span>
          </div>
          <div class="text-right">
            <GilAmount :value="desynth.buyCost" />
            <MutedText size="sm" class="ml-2">
              {{ t('planner.componentsApprox') }} <GilAmount :value="Math.round(returnsOf(desynth))" />
              {{ t('planner.profitApprox') }} <GilAmount :value="Math.round(desynth.valuation.evGross)" />
            </MutedText>
          </div>
        </div>
        <MutedText size="sm" class="mt-1 block">
          {{ t('planner.yields', { list: outputsLabel(desynth) }) }}
        </MutedText>
      </div>
    </div>
  </NeutralContainer>
</template>
