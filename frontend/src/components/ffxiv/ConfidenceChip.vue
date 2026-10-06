/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type { Confidence } from '@/api/offers'

/** Trust tier of a valuation, as computed by the backend. */
defineProps<{ confidence: Confidence }>()

const { t } = useI18n()
</script>

<template>
  <span :title="t('confidence.hint')">
    <SuccessBadge v-if="confidence === 'HIGH'">{{ t('confidence.high') }}</SuccessBadge>
    <InfoBadge v-else-if="confidence === 'MEDIUM'">{{ t('confidence.medium') }}</InfoBadge>
    <ErrorBadge v-else>{{ t('confidence.low') }}</ErrorBadge>
  </span>
</template>
