/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'

const props = withDefaults(defineProps<{
  lambdaUndercut: number
  ghostFraction: number
  modelSufficient?: boolean
  /** DC-pooled fit — trust the price level, question the world-level rate. */
  modelPooled?: boolean
  hideInsufficient?: boolean
}>(), {
  modelSufficient: true,
  modelPooled: false,
  hideInsufficient: false,
})

const { t } = useI18n()

/** Anything meaningfully non-zero is treated as a flag. */
const BOT_THRESHOLD = 0.05
const GHOST_THRESHOLD = 0.1
</script>

<template>
  <div class="flex flex-wrap items-center gap-1">
    <ErrorBadge v-if="lambdaUndercut > BOT_THRESHOLD" :title="t('adversary.botTitle', { rate: lambdaUndercut.toFixed(2) })">
      {{ t('adversary.bot') }}
    </ErrorBadge>
    <ErrorBadge v-if="ghostFraction > GHOST_THRESHOLD" :title="t('adversary.ghostTitle', { pct: (ghostFraction * 100).toFixed(0) })">
      {{ t('adversary.ghost') }}
    </ErrorBadge>
    <InfoBadge
      v-if="modelPooled"
      :title="t('adversary.pooledTitle')"
    >
      {{ t('adversary.pooled') }}
    </InfoBadge>
    <SuccessBadge v-if="!hideInsufficient && modelSufficient" :title="t('adversary.okTitle')">
      {{ t('adversary.ok') }}
    </SuccessBadge>
    <ErrorBadge v-else-if="!hideInsufficient && !modelSufficient" :title="t('adversary.thinTitle')">
      {{ t('adversary.thin') }}
    </ErrorBadge>
    <MutedText
      v-if="lambdaUndercut === 0 && ghostFraction === 0 && modelSufficient && !modelPooled"
      size="sm"
      class="ml-1"
    >
      —
    </MutedText>
  </div>
</template>
