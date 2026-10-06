/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { formatGil } from '@/util/format'

withDefaults(defineProps<{
  value: number | null | undefined
  muted?: boolean
}>(), {
  muted: false,
})
</script>

<template>
  <span :class="muted ? 'text-(--text-muted)' : ''">
    <span class="font-mono">{{ formatGil(value) }}</span>
    <span class="text-(--text-muted) text-xs ml-0.5">g</span>
  </span>
</template>
