/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import CalibrationCard from '@/components/ffxiv/CalibrationCard.vue'
import WorstKeysCard from '@/components/ffxiv/WorstKeysCard.vue'
import TrendsCard from '@/components/ffxiv/TrendsCard.vue'
import OpportunityGrid from '@/components/ffxiv/OpportunityGrid.vue'
import HomeWorldCard from '@/components/ffxiv/HomeWorldCard.vue'
import CalibrationChart from '@/components/ffxiv/CalibrationChart.vue'
import PerfChart from '@/components/ffxiv/PerfChart.vue'
import { dashboardApi } from '@/api'
import type { CalibrationHistoryPoint, CapabilityStats } from '@/api/dashboard'

const { t } = useI18n()
const history = ref<CalibrationHistoryPoint[]>([])
const capability = ref<CapabilityStats | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

async function refresh() {
  loading.value = true
  error.value = null
  // allSettled — one broken endpoint shouldn't wipe the whole dashboard.
  const [hRes, sRes] = await Promise.allSettled([
    dashboardApi.calibrationHistory(24 * 14),
    dashboardApi.stats(),
  ])
  const errors: string[] = []
  if (hRes.status === 'fulfilled') history.value = hRes.value
  else errors.push(hRes.reason instanceof Error ? hRes.reason.message : String(hRes.reason))
  if (sRes.status === 'fulfilled') capability.value = sRes.value
  else errors.push(sRes.reason instanceof Error ? sRes.reason.message : String(sRes.reason))
  error.value = errors.length ? errors.join(' • ') : null
  loading.value = false
}

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = n / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value.toFixed(1)} ${units[unit]}`
}

const stats = computed(() => {
  if (!capability.value) return null
  const s = capability.value
  return [
    {
      label: t('dashboard.fittedModels'),
      value: s.totalModels.toLocaleString(),
      hint: t('dashboard.aboveThreshold', { count: s.sufficientModels.toLocaleString() }),
    },
    {
      label: t('dashboard.uniqueItems'),
      value: s.uniqueItems.toLocaleString(),
      hint: t('dashboard.worldsCount', { count: s.uniqueWorlds.toLocaleString() }),
    },
    {
      label: t('dashboard.salesRecorded'),
      value: s.totalSales.toLocaleString(),
      hint: t('dashboard.liveListings', { count: s.totalListings.toLocaleString() }),
    },
    {
      label: t('dashboard.enabledAlerts'),
      value: s.enabledAlerts.toLocaleString(),
      hint: t('dashboard.firedIn24h', { count: s.alertsFired24h.toLocaleString() }),
    },
    {
      label: t('dashboard.databaseSize'),
      value: formatBytes(s.databaseBytes),
      hint: s.lastRefitAt
        ? t('dashboard.lastRefit', { when: s.lastRefitAt.slice(0, 16).replace('T', ' ') })
        : t('dashboard.noRefit'),
    },
  ]
})

onMounted(refresh)
</script>

<template>
  <PageHeader :title="t('dashboard.title')" :subtitle="t('dashboard.subtitle')" />

  <div class="mt-4">
    <HomeWorldCard />
  </div>

  <div class="mt-4">
    <OpportunityGrid />
  </div>

  <div class="mt-4">
    <CalibrationCard />
  </div>

  <div class="mt-4">
    <TrendsCard />
  </div>

  <div class="mt-4">
    <WorstKeysCard />
  </div>

  <NeutralContainer class="mt-4">
    <SectionHeader>{{ t('dashboard.calibrationOverTime') }}</SectionHeader>
    <MutedText size="sm">
      {{ t('dashboard.calibrationOverTimeHint') }}
    </MutedText>
    <div v-if="loading" class="mt-4 flex justify-center"><Spinner size="lg" /></div>
    <Alert v-else-if="error" variant="error">{{ error }}</Alert>
    <div v-else-if="history.length === 0" class="mt-4">
      <MutedText size="sm">
        {{ t('dashboard.noSnapshots') }}
      </MutedText>
    </div>
    <CalibrationChart v-else :points="history" class="mt-4" />
  </NeutralContainer>

  <NeutralContainer v-if="stats" class="mt-4">
    <SectionHeader>{{ t('dashboard.capacity') }}</SectionHeader>
    <MutedText size="sm">{{ t('dashboard.capacityHint') }}</MutedText>
    <div class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <div
        v-for="s in stats"
        :key="s.label"
        class="rounded-(--radius-theme) border border-(--border) bg-(--bg) p-3"
      >
        <MutedText size="sm">{{ s.label }}</MutedText>
        <div class="text-lg font-semibold">{{ s.value }}</div>
        <MutedText size="sm">{{ s.hint }}</MutedText>
      </div>
    </div>
  </NeutralContainer>

  <NeutralContainer v-if="capability" class="mt-4">
    <SectionHeader>{{ t('dashboard.refreshCadence') }}</SectionHeader>
    <MutedText size="sm">{{ t('dashboard.refreshCadenceHint') }}</MutedText>
    <div class="mt-4 grid gap-4 md:grid-cols-2">
      <PerfChart :points="capability.modelRefitMs" :title="t('dashboard.modelRefitTitle')" />
      <PerfChart :points="capability.viewRefreshMs" :title="t('dashboard.viewRefreshTitle')" />
    </div>
  </NeutralContainer>
</template>
