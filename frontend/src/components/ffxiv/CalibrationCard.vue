/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import { calibrationApi } from '@/api'
import type { CalibrationSnapshot } from '@/api/calibration'

const { t } = useI18n()
const snapshot = ref<CalibrationSnapshot | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const biasPercent = computed(() => {
  if (!snapshot.value) return null
  const value = (Math.expm1(snapshot.value.logRatioMean) * 100)
  return Math.round(value * 10) / 10
})

// One vote per key — immune to a few high-volume items dominating the mean.
const typicalKeyPercent = computed(() => {
  if (!snapshot.value) return null
  const value = (Math.expm1(snapshot.value.keyMedianLogRatio) * 100)
  return Math.round(value * 10) / 10
})

async function refresh() {
  loading.value = true
  error.value = null
  try {
    snapshot.value = await calibrationApi.snapshot()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ t('dashboard.calibrationTitle') }}</SectionHeader>
    <MutedText size="sm">
      {{ t('dashboard.calibrationSubtitle', { days: snapshot?.windowDays ?? 14 }) }}
    </MutedText>

    <div v-if="loading" class="mt-3">
      <MutedText size="sm">{{ t('common.loading') }}</MutedText>
    </div>
    <div v-else-if="error" class="mt-3">
      <MutedText size="sm">{{ t('common.failedToLoad', { error }) }}</MutedText>
    </div>
    <div v-else-if="snapshot" class="mt-4 space-y-2">
      <div class="grid gap-3 text-sm sm:grid-cols-4">
        <div>
          <MutedText size="sm">{{ t('dashboard.observations') }}</MutedText>
          <div class="font-semibold">{{ snapshot.sampleCount.toLocaleString() }}</div>
        </div>
        <div>
          <MutedText size="sm">{{ t('dashboard.biasVolume') }}</MutedText>
          <div class="font-semibold">
            <span v-if="biasPercent != null">{{ biasPercent >= 0 ? '+' : '' }}{{ biasPercent }}%</span>
            <span v-else>—</span>
          </div>
        </div>
        <div :title="t('dashboard.typicalKeyHint')">
          <MutedText size="sm">{{ t('dashboard.typicalKey') }}</MutedText>
          <div class="font-semibold">
            <span v-if="typicalKeyPercent != null">{{ typicalKeyPercent >= 0 ? '+' : '' }}{{ typicalKeyPercent }}%</span>
            <span v-else>—</span>
          </div>
        </div>
        <div>
          <MutedText size="sm">{{ t('dashboard.logRatioSigma') }}</MutedText>
          <div class="font-semibold">{{ snapshot.logRatioSigma.toFixed(3) }}</div>
        </div>
      </div>
      <MutedText size="sm">{{ snapshot.interpretation }}</MutedText>
    </div>
  </NeutralContainer>
</template>
