/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import ShoppingStopCard from '@/components/ffxiv/ShoppingStopCard.vue'
import ShoppingTreeNode from '@/components/ffxiv/ShoppingTreeNode.vue'
import { pushToast } from '@/composables/useToasts'
import type { ShoppingDecision, ShoppingPlan } from '@/api/shopping'

const { t } = useI18n()

const props = defineProps<{
  plan: ShoppingPlan
  hqItemIds: ReadonlySet<number>
}>()

defineEmits<{
  toggleDecision: [itemId: number, decision: ShoppingDecision]
  toggleHq: [itemId: number, hq: boolean]
}>()

/** Retail marketboard tax backed out of the per-unit net for the list price. */
const MB_TAX = 0.05

/** Expected sale proceeds (net of tax) over all `count` products — expectedNet is per unit. */
const expectedSale = computed(() =>
  props.plan.valuation ? Math.round(props.plan.valuation.expectedNet * props.plan.count) : null,
)

/** Per-unit price to type into the retainer form so the net comes out at expectedNet. */
const listAt = computed(() =>
  props.plan.valuation ? Math.max(1, Math.round(props.plan.valuation.expectedNet / (1 - MB_TAX))) : null,
)

/**
 * Teamcraft-syntax export — consumed by the Teamcraft import dialog and
 * various in-game list plugins. The syntax is machine-read; it stays English.
 */
const teamcraftExport = computed<string>(() => {
  const line = (qty: number, name: string) => `${qty}x ${name}`
  const sections: string[] = []
  if (props.plan.preCrafts.length > 0) {
    sections.push(['Pre crafts :', ...props.plan.preCrafts.map((s) => line(s.qty, s.itemName))].join('\n'))
  }
  sections.push(['Items :', line(props.plan.count, props.plan.itemName)].join('\n'))
  return sections.join('\n\n')
})

async function copyTeamcraft() {
  try {
    await navigator.clipboard.writeText(teamcraftExport.value)
    pushToast(t('planner.teamcraftCopied'), 'success')
  } catch {
    pushToast(t('common.clipboardFailed'), 'warning')
  }
}
</script>

<template>
  <div class="grid gap-4">
    <NeutralContainer>
      <div class="flex flex-wrap items-center gap-3">
        <ItemIcon :item-id="plan.itemId" :size="40" :alt="plan.itemName" />
        <div>
          <SubHeader>{{ plan.itemName }}<HQMark :hq="plan.productCanBeHq" /></SubHeader>
          <MutedText size="sm">
            {{ t('shopping.craftMath', { count: plan.count, runs: plan.runs, yield: plan.yield }) }}
          </MutedText>
        </div>
        <span class="ml-auto rounded bg-(--bg-accent) px-1.5 py-0.5 text-xs capitalize text-(--text-muted)">
          {{ plan.craftClass }} L{{ plan.craftLevel }}
        </span>
      </div>
    </NeutralContainer>

    <NeutralContainer>
      <SectionHeader>{{ t('shopping.ingredients') }}</SectionHeader>
      <MutedText size="sm">{{ t('shopping.ingredientsHint') }}</MutedText>
      <div class="mt-3 grid gap-1">
        <ShoppingTreeNode
          v-for="node in plan.tree"
          :key="node.itemId"
          :node="node"
          :hq-item-ids="hqItemIds"
          @toggle-decision="(id, d) => $emit('toggleDecision', id, d)"
          @toggle-hq="(id, v) => $emit('toggleHq', id, v)"
        />
      </div>
    </NeutralContainer>

    <div v-if="plan.stops.length > 0">
      <SectionHeader>{{ t('shopping.shoppingRun') }}</SectionHeader>
      <MutedText size="sm">{{ t('shopping.shoppingRunHint') }}</MutedText>
      <ul class="mt-3 grid gap-3">
        <ShoppingStopCard
          v-for="(stop, i) in plan.stops"
          :key="stop.worldId"
          :stop="stop"
          :index="i"
        />
      </ul>
    </div>

    <NeutralContainer v-if="plan.preCrafts.length > 0">
      <SectionHeader>{{ t('planner.precraftFirst') }}</SectionHeader>
      <div class="mt-2 grid gap-1 text-sm">
        <div
          v-for="step in plan.preCrafts"
          :key="step.itemId"
          class="flex flex-wrap items-baseline justify-between gap-2 rounded-(--radius-theme) bg-(--bg) px-3 py-2"
        >
          <div class="flex flex-1 items-center gap-2">
            <ItemIcon :item-id="step.itemId" :size="20" :alt="step.itemName" />
            <span class="font-medium">{{ step.itemName }}</span>
            <MutedText size="sm">× {{ step.qty }}</MutedText>
          </div>
          <span class="rounded bg-(--bg-accent) px-1.5 py-0.5 text-xs capitalize text-(--text-muted)">
            {{ step.craftClass }} L{{ step.craftLevel }}
          </span>
        </div>
      </div>
    </NeutralContainer>

    <NeutralContainer>
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <MutedText size="sm">{{ t('shopping.totalCost') }}</MutedText>
          <div class="text-lg font-semibold"><GilAmount :value="plan.totalCost" /></div>
        </div>
        <div v-if="plan.valuation" class="text-right text-sm">
          <MutedText size="sm">
            {{ t('shopping.expectedSale') }} <GilAmount :value="expectedSale" />
            · {{ t('planner.listAtPrefix') }} <GilAmount :value="listAt" />
          </MutedText>
          <div class="font-semibold text-(--color-success)">
            <GilAmount :value="Math.round(plan.valuation.evGross)" /> {{ t('planner.profitSuffix') }}
          </div>
        </div>
      </div>
      <div class="mt-3 print:hidden">
        <SecondaryButton @click="copyTeamcraft">
          <FontAwesomeIcon :icon="['fas', 'copy']" class="mr-2" />
          {{ t('planner.copyTeamcraft') }}
        </SecondaryButton>
      </div>
    </NeutralContainer>
  </div>
</template>
