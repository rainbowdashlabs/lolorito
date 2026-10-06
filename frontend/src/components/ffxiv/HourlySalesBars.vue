/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { HourBucket } from '@/api/trends'

/** Compact bar strip of units sold per hour; each bar's tooltip carries the hour, units, and gil. */
const props = defineProps<{ hours: HourBucket[] }>()

const { t } = useI18n()

const peak = computed(() => Math.max(1, ...props.hours.map((h) => h.units)))

function label(h: HourBucket): string {
  const at = new Date(h.hourStart)
  return t('homeWorld.hourTooltip', {
    hour: at.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    units: h.units.toLocaleString(),
    gil: h.gil.toLocaleString(),
  })
}
</script>

<template>
  <div class="flex h-12 items-end gap-px" role="img" :aria-label="t('homeWorld.sparklineLabel')">
    <div
      v-for="h in hours"
      :key="h.hourStart"
      class="flex-1 rounded-t-sm bg-primary/60 hover:bg-primary"
      :style="{ height: `${Math.max(4, (h.units / peak) * 100)}%` }"
      :class="h.units === 0 ? 'opacity-30' : ''"
      :title="label(h)"
    />
  </div>
</template>
