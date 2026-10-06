/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseBadge from '@/components/badge/BaseBadge.vue'

defineProps<{
  worldName: string
  dataCenter?: string
}>()
</script>

<template>
  <BaseBadge bg-class="bg-(--bg-accent) border border-(--border)">
    <span class="font-medium">{{ worldName }}</span>
    <span v-if="dataCenter" class="text-(--text-muted) ml-1">·&nbsp;{{ dataCenter }}</span>
  </BaseBadge>
</template>
