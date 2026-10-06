/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import { useBasket } from '@/composables/useBasket'

defineEmits<{ open: [] }>()

const { t } = useI18n()
const basket = useBasket()
</script>

<template>
  <button
    type="button"
    class="relative flex items-center gap-1 rounded-(--radius-theme) border border-(--border) px-3 py-1 text-sm hover:bg-(--bg-light-accent)"
    :title="t('basket.viewBasket')"
    @click="$emit('open')"
  >
    <FontAwesomeIcon :icon="['fas', 'basket-shopping']" />
    <span class="hidden sm:inline">{{ t('basket.buttonLabel') }}</span>
    <span
      v-if="basket.count.value > 0"
      class="ml-1 inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-xs font-bold text-primary-text"
    >{{ basket.count.value }}</span>
  </button>
</template>
