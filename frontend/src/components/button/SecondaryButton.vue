/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseButton from './BaseButton.vue'

defineProps<{
  disabled?: boolean
  fullWidth?: boolean
  icon?: string[] | null
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <BaseButton
      :disabled="disabled"
      :full-width="fullWidth"
      :icon="icon"
      class="border border-(--border) bg-(--bg-accent) hover:bg-(--bg)"
      @click="$emit('click', $event)"
  >
    <slot/>
  </BaseButton>
</template>
