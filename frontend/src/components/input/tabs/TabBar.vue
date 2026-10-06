/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Horizontal tab strip bound to a string model. The `tab` slot receives
 * each option so callers can add marks or icons next to the label.
 */
const model = defineModel<string>({ required: true })

defineProps<{
  options: { value: string; label: string }[]
  ariaLabel?: string
}>()
</script>

<template>
  <div role="tablist" :aria-label="ariaLabel" class="inline-flex border-b border-(--border)">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      role="tab"
      :aria-selected="model === opt.value"
      class="-mb-px border-b-2 px-4 py-1.5 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      :class="model === opt.value ? 'border-primary text-primary' : 'border-transparent text-(--text-muted) hover:text-(--text)'"
      @click="model = opt.value"
    >
      <slot name="tab" :option="opt">{{ opt.label }}</slot>
    </button>
  </div>
</template>
