/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <div class="mb-6">
    <h1 class="text-2xl sm:text-3xl font-bold">{{ title }}</h1>
    <p v-if="subtitle" class="mt-1 text-(--text-muted)">{{ subtitle }}</p>
  </div>
</template>
