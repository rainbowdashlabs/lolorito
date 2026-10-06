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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import { pushToast } from '@/composables/useToasts'
import type { Plan, PlanCraft, PlanCraftStep } from '@/api/planner'

const { t } = useI18n()
const props = defineProps<{ plan: Plan }>()

/** Pre-craft steps across every craft, same item merged, order preserved (deepest first). */
const intermediates = computed<PlanCraftStep[]>(() => {
  const out: PlanCraftStep[] = []
  for (const craft of props.plan.crafts) {
    for (const step of craft.intermediates) {
      const seen = out.find((s) => s.itemId === step.itemId)
      if (seen) seen.qty += step.qty
      else out.push({ ...step })
    }
  }
  return out
})

/** Retail marketboard tax backed out of the per-unit net for the list price. */
const MB_TAX = 0.05

/** Expected sale proceeds (net of tax) for one craft's products — expectedNet is per unit. */
function revenueOf(craft: PlanCraft): number {
  return craft.valuation.expectedNet * craft.qty
}

/** Per-unit price to type into the retainer form so the net comes out at expectedNet. */
function listAtOf(craft: PlanCraft): number {
  return Math.max(1, Math.round(craft.valuation.expectedNet / (1 - MB_TAX)))
}

const totalRevenue = computed(() => props.plan.crafts.reduce((sum, c) => sum + revenueOf(c), 0))

/**
 * Teamcraft-syntax export — consumed by the Teamcraft import dialog and
 * various in-game list plugins:
 *
 *   Pre crafts :
 *   4x Iron Ingot
 *
 *   Items :
 *   1x Iron Longsword
 */
const teamcraftExport = computed<string>(() => {
  const line = (qty: number, name: string) => `${qty}x ${name}`
  const sections: string[] = []
  if (intermediates.value.length > 0) {
    sections.push(['Pre crafts :', ...intermediates.value.map((s) => line(s.qty, s.itemName))].join('\n'))
  }
  const items = new Map<string, number>()
  for (const craft of props.plan.crafts) {
    items.set(craft.itemName, (items.get(craft.itemName) ?? 0) + craft.qty)
  }
  sections.push(['Items :', ...[...items.entries()].map(([name, qty]) => line(qty, name))].join('\n'))
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
  <NeutralContainer v-if="plan.crafts.length > 0" class="mt-3 print:break-inside-avoid">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <SectionHeader>{{ t('planner.craftAtHome') }}</SectionHeader>
        <MutedText size="sm">
          {{ t('planner.craftAtHomeHint', { world: plan.homeWorldName }) }}
        </MutedText>
      </div>
      <div class="text-right text-sm">
        <MutedText size="sm">
          {{ t('planner.materialsPrefix') }} <GilAmount :value="plan.craftMaterialsCost" />
          {{ t('planner.saleApprox') }} <GilAmount :value="Math.round(totalRevenue)" />
        </MutedText>
        <div class="font-semibold text-(--color-success)">
          <GilAmount :value="Math.round(plan.craftEvGross)" /> {{ t('planner.profitSuffix') }}
        </div>
      </div>
    </div>

    <div v-if="intermediates.length > 0" class="mt-3">
      <MutedText size="sm">{{ t('planner.precraftFirst') }}</MutedText>
      <div class="mt-1 grid gap-1 text-sm">
        <div
          v-for="step in intermediates"
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
    </div>

    <div class="mt-3">
      <MutedText size="sm">{{ t('planner.thenCraftList') }}</MutedText>
      <div class="mt-1 grid gap-1 text-sm">
        <div
          v-for="craft in plan.crafts"
          :key="craft.uniqueKey"
          class="flex flex-wrap items-baseline justify-between gap-2 rounded-(--radius-theme) bg-(--bg) px-3 py-2"
        >
          <div class="flex flex-1 items-center gap-2">
            <ItemIcon :item-id="craft.itemId" :size="24" :alt="craft.itemName" />
            <span class="font-medium">{{ craft.itemName }}</span>
            <MutedText size="sm">{{ t('planner.craftYield', { n: craft.qty }, craft.qty) }}</MutedText>
            <span
              class="rounded bg-(--bg-accent) px-1.5 py-0.5 text-xs capitalize text-(--text-muted)"
              :title="craft.craftVerified
                ? t('planner.craftReqVerified', { class: craft.craftClass, level: craft.craftLevel })
                : t('planner.craftReqUnverified', { class: craft.craftClass, level: craft.craftLevel })"
            >
              {{ craft.craftClass }} L{{ craft.craftLevel }}<span v-if="!craft.craftVerified"> ?</span>
            </span>
            <MutedText v-if="craft.depthCapped" size="sm" :title="t('item.depthCappedHint')">
              {{ t('item.depthCapped') }}
            </MutedText>
          </div>
          <div class="text-right">
            {{ t('planner.listAtPrefix') }} <GilAmount :value="listAtOf(craft)" />
            <MutedText size="sm" class="ml-2">
              {{ t('planner.materialsMid') }} <GilAmount :value="craft.materialsCost" />
              {{ t('planner.profitApprox') }} <GilAmount :value="Math.round(craft.valuation.evGross)" />
            </MutedText>
          </div>
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
</template>
