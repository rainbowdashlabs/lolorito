/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseInput from '../BaseInput.vue'

const model = defineModel<number | undefined>()

defineProps<{
  placeholder?: string
  disabled?: boolean
  min?: number | string
  max?: number | string
  step?: number | string
  borderless?: boolean
}>()
</script>

<template>
  <BaseInput
      v-model="model"
      :disabled="disabled"
      :placeholder="placeholder"
      :step="step != null ? String(step) : undefined"
      :borderless="borderless"
      type="number"
      :min="min != null ? String(min) : undefined"
      :max="max != null ? String(max) : undefined"
  />
</template>
