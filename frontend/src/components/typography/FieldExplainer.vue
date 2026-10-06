/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MutedText from '@/components/typography/MutedText.vue'
</script>

<template>
  <details class="mt-1">
    <summary
      class="cursor-pointer select-none text-xs text-(--text-muted) underline decoration-dotted underline-offset-2"
    >
      What does it do?
    </summary>
    <div class="mt-1">
      <MutedText size="sm">
        <slot />
      </MutedText>
    </div>
  </details>
</template>
