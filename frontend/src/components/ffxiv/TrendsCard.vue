/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TrendTable from '@/components/ffxiv/TrendTable.vue'
import { board } from '@/api/trends'
import type { TrendBoard } from '@/api/trends'

const { t } = useI18n()

const data = ref<TrendBoard | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function refresh() {
  loading.value = true
  error.value = null
  try {
    data.value = await board({ limit: 8 })
  } catch (e: unknown) {
    const obj = e as { response?: { data?: { error?: string } }; message?: string }
    error.value = obj?.response?.data?.error ?? obj?.message ?? 'error'
  } finally {
    loading.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ t('trends.title') }}</SectionHeader>
    <MutedText size="sm">{{ t('trends.subtitle', { days: data?.windowDays ?? 7 }) }}</MutedText>

    <div v-if="loading" class="mt-3">
      <MutedText size="sm">{{ t('common.loading') }}</MutedText>
    </div>
    <div v-else-if="error" class="mt-3">
      <MutedText size="sm">{{ t('common.failedToLoad', { error }) }}</MutedText>
    </div>
    <div v-else-if="data" class="mt-4 grid gap-6 lg:grid-cols-2">
      <div>
        <div class="mb-2 text-sm font-semibold text-(--color-success)">{{ t('trends.trending') }}</div>
        <TrendTable :rows="data.trending" />
      </div>
      <div>
        <div class="mb-2 text-sm font-semibold text-(--color-error)">{{ t('trends.losing') }}</div>
        <TrendTable :rows="data.losing" />
      </div>
    </div>
  </NeutralContainer>
</template>
