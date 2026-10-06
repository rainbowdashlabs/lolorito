/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  hq: boolean
}>()
</script>

<template>
  <!-- U+E03C is the game's HQ glyph in the Lodestone private-use area. -->
  <span
    v-if="hq"
    class="ffxiv-symbol inline-flex items-center px-0.5 text-primary-accent"
    :title="t('common.highQuality')"
  >&#xE03C;</span>
</template>
