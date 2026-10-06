/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import PlanSummaryCard from '@/components/ffxiv/PlanSummaryCard.vue'
import PlanStopCard from '@/components/ffxiv/PlanStopCard.vue'
import PlanCraftsCard from '@/components/ffxiv/PlanCraftsCard.vue'
import PlanDesynthsCard from '@/components/ffxiv/PlanDesynthsCard.vue'
import RetainerBasketCard from '@/components/ffxiv/RetainerBasketCard.vue'
import SellRecommendationCard from '@/components/ffxiv/SellRecommendationCard.vue'
import { computed } from 'vue'
import type { Plan } from '@/api/planner'

const { t } = useI18n()

const props = defineProps<{
  plan: Plan
  completed: Set<number>
  hopWeightGilPerSecond: number
}>()

defineEmits<{
  toggleDone: [index: number]
  print: []
}>()

function fromLabelFor(i: number): string {
  return i === 0 ? props.plan.homeWorldName : props.plan.stops[i - 1].worldName
}

/**
 * Ordered stop indices: pending first (in the solver's original order),
 * then completed stops at the bottom in the order they were checked off.
 * This keeps the "what's next" list at the top and the "what to sell now"
 * list at the bottom so the player always sees actionable info nearby.
 */
const orderedIndices = computed<number[]>(() => {
  const pending: number[] = []
  const done: number[] = []
  props.plan.stops.forEach((_, i) => {
    if (props.completed.has(i)) done.push(i)
    else pending.push(i)
  })
  return [...pending, ...done]
})

/**
 * True once every stop has been ticked done. When true we still surface
 * the aggregate "Ready to list" card at the bottom — handy for a single
 * printable summary — but every stop already has its own list-price
 * inline so the player can act on each one as they go.
 */
const allCollected = computed(
  () => props.plan.stops.length > 0 && props.plan.stops.every((_, i) => props.completed.has(i)),
)
</script>

<template>
  <PlanSummaryCard :plan="plan" @print="$emit('print')" />

  <ol class="space-y-3">
    <PlanStopCard
      v-for="i in orderedIndices"
      :key="plan.stops[i].worldId"
      :stop="plan.stops[i]"
      :index="i"
      :from-label="fromLabelFor(i)"
      :done="completed.has(i)"
      @toggle-done="$emit('toggleDone', i)"
    />
  </ol>

  <SuccessContainer class="mt-3 print:break-inside-avoid">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <SectionHeader>{{ t('planner.returnToHome', { home: plan.homeWorldName }) }}</SectionHeader>
        <MutedText size="sm">{{ t('planner.listWaitHarvest') }}</MutedText>
      </div>
      <div class="text-right">
        <MutedText size="sm">{{ t('planner.returnHop') }}</MutedText>
        <div class="font-semibold">{{ plan.hopSecondsToHome }}s</div>
      </div>
    </div>
  </SuccessContainer>

  <PlanCraftsCard :plan="plan" />

  <PlanDesynthsCard :plan="plan" />

  <RetainerBasketCard :plan="plan" />

  <SellRecommendationCard v-if="allCollected" :plan="plan" />

  <div class="mt-4 print:hidden">
    <MutedText size="sm">
      {{ t('planner.objectiveExplainer', { weight: hopWeightGilPerSecond }) }}
    </MutedText>
  </div>
</template>
