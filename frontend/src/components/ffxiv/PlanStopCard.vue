/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import WorldBadge from '@/components/ffxiv/WorldBadge.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import { recommendationFor, type SellRecommendation } from '@/composables/useSellRecommendation'
import type { PlanStop } from '@/api/planner'

const { t } = useI18n()

const props = defineProps<{
  stop: PlanStop
  index: number
  fromLabel: string
  done: boolean
}>()

defineEmits<{ toggleDone: [] }>()

/**
 * Per-buy sell recommendations for the stop. Only used when the stop is
 * marked done — the player is holding the bags and needs to know what to
 * list them at. Material lines drop out: they feed a craft, not a listing.
 */
const recommendations = computed<SellRecommendation[]>(() =>
  props.stop.buys
    .map((b) => recommendationFor(b))
    .filter((r): r is SellRecommendation => r !== null),
)
</script>

<template>
  <li
    class="rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) p-4 print:break-inside-avoid"
    :class="done ? 'opacity-60' : ''"
  >
    <div class="flex flex-wrap items-baseline gap-3">
      <span class="text-lg font-bold">{{ index + 1 }}.</span>
      <WorldBadge :world-name="stop.worldName" />
      <MutedText size="sm">· {{ stop.dataCenterName }}</MutedText>
      <MutedText size="sm">
        <FontAwesomeIcon :icon="['fas', 'location-dot']" class="mr-1" />
        {{ t('planner.hopFrom', { seconds: stop.hopSecondsFromPrev, from: fromLabel }) }}
      </MutedText>
      <div class="ml-auto flex gap-2 print:hidden">
        <button
          type="button"
          class="rounded-(--radius-theme) border border-(--border) px-3 py-1 text-sm hover:bg-(--bg)"
          :aria-pressed="done"
          @click="$emit('toggleDone')"
        >
          <FontAwesomeIcon :icon="['fas', done ? 'check' : 'flag-checkered']" class="mr-1" />
          {{ done ? t('planner.done') : t('planner.markDone') }}
        </button>
      </div>
    </div>
    <div class="mt-3 grid gap-2 text-sm">
      <div
        v-for="buy in stop.buys"
        :key="buy.uniqueKey"
        class="flex flex-wrap items-baseline justify-between gap-2 rounded-(--radius-theme) bg-(--bg) px-3 py-2"
      >
        <div class="flex flex-1 items-center gap-2">
          <ItemIcon :item-id="buy.itemId" :size="24" :alt="buy.itemName" />
          <span class="font-medium">{{ buy.itemName }}</span>
          <HQMark :hq="buy.hq" />
          <MutedText size="sm" class="ml-2">· {{ buy.action.toLowerCase() }}</MutedText>
          <span
            v-if="buy.craftClass"
            class="rounded bg-(--bg-accent) px-1.5 py-0.5 text-xs capitalize text-(--text-muted)"
            :title="buy.craftVerified
              ? t('planner.craftReqVerified', { class: buy.craftClass, level: buy.craftLevel })
              : t('planner.craftReqUnverifiedLong', { class: buy.craftClass, level: buy.craftLevel })"
          >
            {{ buy.craftClass }} L{{ buy.craftLevel }}<span v-if="!buy.craftVerified"> ?</span>
          </span>
        </div>
        <div class="text-right">
          {{ buy.qty }} × <GilAmount :value="buy.buyPrice" />
          <MutedText v-if="buy.valuation" size="sm" class="ml-2">
            {{ t('planner.evArrow') }} <GilAmount :value="buy.valuation.evGross" />
          </MutedText>
          <MutedText v-else size="sm" class="ml-2">{{ t('planner.craftInput') }}</MutedText>
        </div>
      </div>
    </div>
    <div class="mt-3 flex items-baseline justify-between text-sm">
      <MutedText size="sm">{{ t('planner.stopTotal') }}</MutedText>
      <span class="font-semibold">
        {{ t('planner.unitsSlots', { qty: stop.totalQty, slots: stop.totalSlots }) }} ·
        <GilAmount :value="stop.buyCost" />
      </span>
    </div>
    <div
      v-if="done"
      class="mt-3 rounded-(--radius-theme) border border-(--color-success) bg-(--bg) p-3"
    >
      <SectionHeader>{{ t('planner.readyToList') }}</SectionHeader>
      <MutedText size="sm">
        {{ t('planner.readyToListHint') }}
      </MutedText>
      <div class="mt-2 grid gap-2 text-sm">
        <div
          v-for="rec in recommendations"
          :key="rec.itemId + (rec.hq ? '-hq' : '-nq')"
          class="flex flex-wrap items-baseline justify-between gap-2 rounded-(--radius-theme) bg-(--bg-accent) px-3 py-2"
        >
          <div class="flex flex-1 items-center gap-2">
            <ItemIcon :item-id="rec.itemId" :size="20" :alt="rec.itemName" />
            <span class="font-medium">{{ rec.itemName }}</span>
            <HQMark :hq="rec.hq" />
            <MutedText size="sm" class="ml-2">
              {{ t('planner.stacksOf', { stacks: rec.stacks, size: rec.suggestedStackSize }, rec.suggestedStackSize) }}
            </MutedText>
          </div>
          <div class="text-right">
            {{ t('planner.listAtPrefix') }} <GilAmount :value="rec.listAt" />
            <MutedText size="sm" class="ml-2">
              {{ t('planner.netApprox') }} <GilAmount :value="Math.round(rec.expectedNet)" />
            </MutedText>
          </div>
        </div>
      </div>
    </div>
  </li>
</template>
