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
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import type { Plan } from '@/api/planner'
import { formatHours } from '@/util/format'

const { t } = useI18n()
const props = defineProps<{ plan: Plan }>()
defineEmits<{ print: [] }>()

const totalRunSeconds = computed(() => props.plan.totalHopSeconds + props.plan.totalAttentionHours * 3600)

/**
 * True when the retainer basket meaningfully contributes to the plan.
 * When empty, the split subtitles collapse to the plain total so we
 * don't render "of which retainer: 0" clutter.
 */
const hasRetainer = computed(() => props.plan.retainerBasket.length > 0)

/** Live-side totals: whatever the summary total leaves over after subtracting retainer. */
const liveEvGross = computed(() => props.plan.totalEvGross - props.plan.retainerEvGross)
const liveSpend = computed(() => props.plan.totalBuyCost - props.plan.retainerSpend)
const liveQty = computed(() => props.plan.totalQty - props.plan.retainerQty)
const liveObjective = computed(() => props.plan.objective - props.plan.retainerObjective)
const allAttentionHours = computed(() => props.plan.totalAttentionHours + props.plan.retainerAttentionHours)

/**
 * Detects near-substitute picks — the same item id in different qualities,
 * or the same item family showing up more than once in a stop or in the
 * retainer basket. Real cluster modelling in the solver may come later;
 * this UI hint softens the sharp edge in the meantime.
 */
const substituteClusters = computed<string[]>(() => {
  const perItem = new Map<number, { name: string; nq: boolean; hq: boolean; count: number }>()
  const track = (itemId: number, itemName: string, hq: boolean) => {
    const cell = perItem.get(itemId) ?? { name: itemName, nq: false, hq: false, count: 0 }
    if (hq) cell.hq = true
    else cell.nq = true
    cell.count += 1
    perItem.set(itemId, cell)
  }
  // Material lines aren't sold, so they can't compete for buyers.
  props.plan.stops.forEach((s) =>
    s.buys.filter((b) => b.action !== 'MATERIAL').forEach((b) => track(b.itemId, b.itemName, b.hq)),
  )
  props.plan.retainerBasket.forEach((r) => track(r.itemId, r.itemName, r.hq))
  const notes: string[] = []
  for (const { name, nq, hq, count } of perItem.values()) {
    if (nq && hq) notes.push(t('planner.subBothQualities', { name }))
    else if (count >= 3) notes.push(t('planner.subAppears', { name, count }))
  }
  return notes
})
</script>

<template>
  <PrimaryContainer class="mb-4 print:mb-2">
    <div class="flex flex-wrap items-baseline justify-between gap-4">
      <div>
        <SectionHeader>{{ t('planner.planFor', { world: plan.homeWorldName }) }}</SectionHeader>
        <MutedText size="sm">
          {{ t('planner.stopCount', { count: plan.stops.length }, plan.stops.length) }}
          {{ t('planner.planMeta', { subsets: plan.subsetsConsidered, candidates: plan.candidatesConsidered }) }}
        </MutedText>
      </div>
      <div class="flex gap-2 print:hidden">
        <SecondaryButton @click="$emit('print')">
          <FontAwesomeIcon :icon="['fas', 'print']" class="mr-2" />
          {{ t('common.print') }}
        </SecondaryButton>
      </div>
    </div>
    <div
      v-if="substituteClusters.length > 0"
      class="mt-3 rounded-(--radius-theme) border border-(--color-warning) bg-(--bg) px-3 py-2 text-sm"
    >
      <MutedText size="sm">{{ t('planner.substitutes') }}</MutedText>
      <div class="mt-1">
        {{ t('planner.substitutesHint') }}
      </div>
      <ul class="mt-1 list-disc pl-5 text-xs">
        <li v-for="note in substituteClusters" :key="note">{{ note }}</li>
      </ul>
    </div>

    <div
      v-if="plan.crafts.length > 0"
      class="mt-3 rounded-(--radius-theme) border border-(--border) bg-(--bg) px-3 py-2 text-sm"
    >
      <MutedText size="sm">{{ t('planner.crafts') }}</MutedText>
      <div class="mt-1 flex flex-wrap items-baseline gap-4">
        <div>
          <MutedText size="sm">{{ t('planner.recipes') }}</MutedText>
          <div class="font-semibold">{{ plan.crafts.length }}</div>
        </div>
        <div>
          <MutedText size="sm">{{ t('planner.materialsLabel') }}</MutedText>
          <div class="font-semibold"><GilAmount :value="plan.craftMaterialsCost" /></div>
        </div>
        <div>
          <MutedText size="sm">{{ t('planner.expectedProfit') }}</MutedText>
          <div class="font-semibold text-(--color-success)">
            <GilAmount :value="Math.round(plan.craftEvGross)" />
          </div>
        </div>
      </div>
    </div>

    <div
      v-if="plan.desynths.length > 0"
      class="mt-3 rounded-(--radius-theme) border border-(--border) bg-(--bg) px-3 py-2 text-sm"
    >
      <MutedText size="sm">{{ t('planner.desynthsLabel') }}</MutedText>
      <div class="mt-1 flex flex-wrap items-baseline gap-4">
        <div>
          <MutedText size="sm">{{ t('planner.sourcesLabel') }}</MutedText>
          <div class="font-semibold">{{ plan.desynths.length }}</div>
        </div>
        <div>
          <MutedText size="sm">{{ t('planner.buyCost') }}</MutedText>
          <div class="font-semibold"><GilAmount :value="plan.desynthBuyCost" /></div>
        </div>
        <div>
          <MutedText size="sm">{{ t('planner.expectedProfit') }}</MutedText>
          <div class="font-semibold text-(--color-success)">
            <GilAmount :value="Math.round(plan.desynthEvGross)" />
          </div>
        </div>
      </div>
    </div>

    <div
      v-if="plan.retainerBasket.length > 0"
      class="mt-3 rounded-(--radius-theme) border border-(--border) bg-(--bg) px-3 py-2 text-sm"
    >
      <MutedText size="sm">{{ t('planner.retainerBasket') }}</MutedText>
      <div class="mt-1 flex flex-wrap items-baseline gap-4">
        <div>
          <MutedText size="sm">{{ t('planner.picks') }}</MutedText>
          <div class="font-semibold">{{ plan.retainerBasket.length }}</div>
        </div>
        <div>
          <MutedText size="sm">{{ t('planner.spend') }}</MutedText>
          <div class="font-semibold"><GilAmount :value="plan.retainerSpend" /></div>
        </div>
        <div>
          <MutedText size="sm">{{ t('planner.units') }}</MutedText>
          <div class="font-semibold">{{ plan.retainerQty }}</div>
        </div>
        <div>
          <MutedText size="sm">{{ t('planner.evGrossRetainer') }}</MutedText>
          <div class="font-semibold"><GilAmount :value="Math.round(plan.retainerEvGross)" /></div>
        </div>
      </div>
    </div>

    <div class="mt-4 grid gap-3 text-sm sm:grid-cols-2 md:grid-cols-4">
      <div>
        <MutedText
          size="sm"
          :title="hasRetainer ? t('planner.objectiveAllHint') : ''"
        >
          {{ hasRetainer ? t('planner.objectiveAll') : t('planner.objective') }}
        </MutedText>
        <div class="font-semibold text-success"><GilAmount :value="plan.objective" /></div>
        <MutedText v-if="hasRetainer" size="xs" class="mt-0.5 block">
          {{ t('planner.liveLabel') }} <GilAmount :value="Math.round(liveObjective)" /> ·
          {{ t('planner.retainerLabel') }} <GilAmount :value="Math.round(plan.retainerObjective)" />
        </MutedText>
      </div>
      <div>
        <MutedText size="sm">{{ t('planner.evGross') }}</MutedText>
        <div class="font-semibold"><GilAmount :value="plan.totalEvGross" /></div>
        <MutedText v-if="hasRetainer" size="xs" class="mt-0.5 block">
          {{ t('planner.liveLabel') }} <GilAmount :value="Math.round(liveEvGross)" /> ·
          {{ t('planner.retainerLabel') }} <GilAmount :value="Math.round(plan.retainerEvGross)" />
        </MutedText>
      </div>
      <div>
        <MutedText size="sm">{{ t('planner.spend') }}</MutedText>
        <div class="font-semibold"><GilAmount :value="plan.totalBuyCost" /></div>
        <MutedText v-if="hasRetainer" size="xs" class="mt-0.5 block">
          {{ t('planner.liveLabel') }} <GilAmount :value="liveSpend" /> ·
          {{ t('planner.retainerLabel') }} <GilAmount :value="plan.retainerSpend" />
        </MutedText>
      </div>
      <div>
        <MutedText size="sm">{{ t('planner.units') }}</MutedText>
        <div class="font-semibold">{{ plan.totalQty }}</div>
        <MutedText v-if="hasRetainer" size="xs" class="mt-0.5 block">
          {{ t('planner.liveLabel') }} {{ liveQty }} · {{ t('planner.retainerLabel') }} {{ plan.retainerQty }}
        </MutedText>
      </div>
      <div>
        <MutedText size="sm">{{ t('planner.hopWallClock') }}</MutedText>
        <div class="font-semibold">{{ formatHours(plan.totalHopSeconds / 3600) }}</div>
        <MutedText v-if="hasRetainer" size="xs" class="mt-0.5 block">{{ t('planner.liveOnly') }}</MutedText>
      </div>
      <div>
        <MutedText size="sm">{{ t('planner.attentionCost') }}</MutedText>
        <div class="font-semibold">{{ formatHours(allAttentionHours) }}</div>
        <MutedText v-if="hasRetainer" size="xs" class="mt-0.5 block">
          {{ t('planner.liveLabel') }} {{ formatHours(plan.totalAttentionHours) }} ·
          {{ t('planner.retainerLabel') }} {{ formatHours(plan.retainerAttentionHours) }}
        </MutedText>
      </div>
      <div>
        <MutedText size="sm">{{ t('planner.estTotalRun') }}</MutedText>
        <div class="font-semibold">{{ formatHours(totalRunSeconds / 3600) }}</div>
      </div>
    </div>
  </PrimaryContainer>
</template>
