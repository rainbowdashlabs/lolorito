/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import type { AlertKind } from '@/api/alerts'

/** One-line reading of a rule's trigger, e.g. "below 10,000 gil" or "≤ 2 listings". */
defineProps<{ kind: AlertKind; threshold: number }>()

const { t } = useI18n()
</script>

<template>
  <span v-if="kind === 'price_below'">{{ t('alerts.below') }} <GilAmount :value="threshold" /></span>
  <span v-else-if="kind === 'price_above'">{{ t('alerts.above') }} <GilAmount :value="threshold" /></span>
  <span v-else-if="kind === 'sale_volume_spike'">{{ t('alerts.conditionSpike', { pct: threshold.toLocaleString() }) }}</span>
  <span v-else>{{ t('alerts.conditionCount', { n: threshold.toLocaleString() }) }}</span>
</template>
