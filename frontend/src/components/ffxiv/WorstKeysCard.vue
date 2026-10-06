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
import THead from '@/components/table/THead.vue'
import Th from '@/components/table/Th.vue'
import TRow from '@/components/table/TRow.vue'
import Td from '@/components/table/Td.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import WorldBadge from '@/components/ffxiv/WorldBadge.vue'
import { calibrationApi } from '@/api'
import type { KeyCalibration } from '@/api/calibration'

const { t } = useI18n()
const keys = ref<KeyCalibration[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

function biasLabel(row: KeyCalibration): string {
  const pct = Math.round(row.bias * 1000) / 10
  return `${pct >= 0 ? '+' : ''}${pct}%`
}

async function refresh() {
  loading.value = true
  error.value = null
  try {
    keys.value = await calibrationApi.worstKeys(15)
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
    <SectionHeader>{{ t('dashboard.worstKeys') }}</SectionHeader>
    <MutedText size="sm">
      {{ t('dashboard.worstKeysHint') }}
    </MutedText>

    <div v-if="loading" class="mt-3">
      <MutedText size="sm">{{ t('common.loading') }}</MutedText>
    </div>
    <div v-else-if="error" class="mt-3">
      <MutedText size="sm">{{ t('common.failedToLoad', { error }) }}</MutedText>
    </div>
    <div v-else-if="keys.length === 0" class="mt-3">
      <MutedText size="sm">{{ t('dashboard.noResiduals') }}</MutedText>
    </div>
    <div v-else class="mt-3 -mx-4 overflow-x-auto px-4 sm:mx-0 sm:px-0">
      <table class="w-full min-w-[480px] text-sm">
        <THead>
          <Th class="uppercase text-xs tracking-wider text-(--text-muted)">{{ t('offers.col.item') }}</Th>
          <Th class="uppercase text-xs tracking-wider text-(--text-muted)">{{ t('alerts.world') }}</Th>
          <Th align="right" class="uppercase text-xs tracking-wider text-(--text-muted)">{{ t('dashboard.obs') }}</Th>
          <Th align="right" class="uppercase text-xs tracking-wider text-(--text-muted)">
            <span :title="t('dashboard.biasHint')" class="border-b border-dotted border-(--text-muted)">
              {{ t('dashboard.bias') }}
            </span>
          </Th>
        </THead>
        <tbody>
          <TRow v-for="row in keys" :key="`${row.itemId}-${row.worldId}-${row.hq}`">
            <Td>
              <router-link
                :to="{ name: 'item', params: { id: String(row.itemId) } }"
                class="inline-flex items-center gap-2 hover:text-(--color-primary-accent) hover:underline"
              >
                <ItemIcon :item-id="row.itemId" :size="20" :alt="row.itemName" />
                <span>{{ row.itemName }}</span>
                <HQMark :hq="row.hq" />
              </router-link>
            </Td>
            <Td><WorldBadge :world-name="row.worldName" /></Td>
            <Td align="right">{{ row.count }}</Td>
            <Td align="right">
              <span
                class="font-medium"
                :class="Math.abs(row.bias) > 0.15 ? 'text-(--color-error)' : ''"
                :title="`σ(log) ${row.logRatioSigma.toFixed(3)}`"
              >
                {{ biasLabel(row) }}
              </span>
            </Td>
          </TRow>
        </tbody>
      </table>
    </div>
  </NeutralContainer>
</template>
