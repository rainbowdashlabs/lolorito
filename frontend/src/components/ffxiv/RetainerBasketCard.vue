/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import WorldBadge from '@/components/ffxiv/WorldBadge.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import type { Plan } from '@/api/planner'

const { t } = useI18n()

defineProps<{ plan: Plan }>()
</script>

<template>
  <NeutralContainer v-if="plan.retainerBasket.length > 0" class="mt-3 print:break-inside-avoid">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <SectionHeader>{{ t('planner.retainerBasket') }}</SectionHeader>
        <MutedText size="sm">
          {{ t('planner.retainerBasketHint') }}
        </MutedText>
      </div>
      <div class="text-right text-sm">
        <MutedText size="sm">
          {{ t('planner.listingCount', { count: plan.retainerBasket.length }, plan.retainerBasket.length) }} ·
          {{ t('planner.unitCount', { count: plan.retainerQty }, plan.retainerQty) }}
        </MutedText>
        <div class="font-semibold"><GilAmount :value="plan.retainerSpend" /> {{ t('planner.spendSuffix') }}</div>
        <div class="font-semibold text-(--color-success)">
          <GilAmount :value="plan.retainerEvGross" /> {{ t('planner.evSuffix') }}
        </div>
      </div>
    </div>
    <ul class="mt-3 space-y-2 text-sm">
      <li
        v-for="pick in plan.retainerBasket"
        :key="pick.uniqueKey"
        class="rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) p-3"
      >
        <div class="flex items-start gap-3">
          <ItemIcon :item-id="pick.itemId" :size="28" :alt="pick.itemName" />
          <div class="min-w-0 flex-1">
            <div class="font-medium">{{ pick.itemName }}<HQMark :hq="pick.hq" /></div>
            <div class="mt-1"><WorldBadge :world-name="pick.sourceWorldName" /></div>
            <div class="mt-1">
              {{ pick.qty }} × <GilAmount :value="pick.buyPrice" />
              <MutedText size="sm" class="ml-1">
                {{ t('planner.retainerEvPerHour') }} <GilAmount :value="pick.retainerEvPerHour" />
              </MutedText>
            </div>
          </div>
        </div>
      </li>
    </ul>
  </NeutralContainer>
</template>
