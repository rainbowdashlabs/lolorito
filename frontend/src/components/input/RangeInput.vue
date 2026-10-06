/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
const model = defineModel<number>({ required: true })

withDefaults(
  defineProps<{
    min: number
    max: number
    step?: number
    display?: (v: number) => string
  }>(),
  { step: 0.01 },
)
</script>

<template>
  <div class="flex items-center gap-3">
    <input
      v-model.number="model"
      type="range"
      :min="min"
      :max="max"
      :step="step"
      class="flex-1 accent-(--color-info)"
    />
    <span class="min-w-14 text-right font-mono text-sm">
      {{ display ? display(model) : model }}
    </span>
  </div>
</template>
