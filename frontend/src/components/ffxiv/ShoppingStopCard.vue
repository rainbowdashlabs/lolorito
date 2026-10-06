/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import WorldBadge from '@/components/ffxiv/WorldBadge.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import type { ShoppingStop } from '@/api/shopping'

const { t } = useI18n()

defineProps<{
  stop: ShoppingStop
  index: number
}>()
</script>

<template>
  <li class="rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) p-4 print:break-inside-avoid">
    <div class="flex flex-wrap items-baseline gap-3">
      <span class="text-lg font-bold">{{ index + 1 }}.</span>
      <WorldBadge :world-name="stop.worldName" />
      <span class="ml-auto font-semibold">
        <MutedText size="sm">{{ t('shopping.stopCost') }}</MutedText>
        <GilAmount :value="stop.cost" class="ml-2" />
      </span>
    </div>
    <div class="mt-3 grid gap-2 text-sm">
      <div
        v-for="line in stop.lines"
        :key="`${line.itemId}-${line.hq ? 'hq' : 'nq'}`"
        class="flex flex-wrap items-baseline justify-between gap-2 rounded-(--radius-theme) bg-(--bg) px-3 py-2"
      >
        <div class="flex flex-1 items-center gap-2">
          <ItemIcon :item-id="line.itemId" :size="24" :alt="line.itemName" />
          <span class="font-medium">{{ line.itemName }}</span>
          <HQMark :hq="line.hq" />
        </div>
        <div class="text-right">
          {{ line.qty }} × <GilAmount :value="line.unitPrice" />
          <MutedText size="sm" class="ml-2">= <GilAmount :value="line.totalCost" /></MutedText>
        </div>
      </div>
    </div>
  </li>
</template>
