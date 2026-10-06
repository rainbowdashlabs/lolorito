/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import THead from '@/components/table/THead.vue'
import Th from '@/components/table/Th.vue'
import TRow from '@/components/table/TRow.vue'
import Td from '@/components/table/Td.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import ItemModelCard from '@/components/ffxiv/ItemModelCard.vue'
import SalesHistoryChart from '@/components/ffxiv/SalesHistoryChart.vue'
import ItemTrendStrip from '@/components/ffxiv/ItemTrendStrip.vue'
import SellPriceCard from '@/components/ffxiv/SellPriceCard.vue'
import ItemActionsPanel from '@/components/ffxiv/ItemActionsPanel.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import TabBar from '@/components/input/tabs/TabBar.vue'
import { itemsApi } from '@/api'
import type { ItemDetailDto } from '@/api/items'
import { useValuationRefresh } from '@/composables/useValuationRefresh'

const { t } = useI18n()
const route = useRoute()
const item = ref<ItemDetailDto | null>(null)
const salesBuckets = ref<import('@/api/items').SalesBucket[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const hqMode = ref<'nq' | 'hq'>('nq')

const itemId = () => Number(route.params.id)

async function refresh() {
  loading.value = true
  error.value = null
  try {
    const [detail, history] = await Promise.all([
      itemsApi.detail(itemId(), hqMode.value === 'hq'),
      itemsApi.salesHistory(itemId(), hqMode.value === 'hq'),
    ])
    item.value = detail
    salesBuckets.value = history
  } catch (e: unknown) {
    const obj = e as { response?: { data?: { error?: string } }; message?: string }
    error.value = obj?.response?.data?.error ?? obj?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (route.query.hq === '1') hqMode.value = 'hq'
  refresh()
})

watch(hqMode, refresh)
watch(() => route.params.id, refresh)

/**
 * Refit-driven refresh — only for the item we're viewing and only when
 * the HQ side matches (an NQ refit doesn't invalidate the HQ view).
 */
useValuationRefresh(
  (evt) => evt.itemId === itemId() && evt.hq === (hqMode.value === 'hq'),
  refresh,
)
</script>

<template>
  <div v-if="loading && !item" class="py-12 text-center">
    <Spinner size="lg" />
  </div>
  <Alert v-else-if="error" variant="error">
    {{ t('item.failedToLoad', { error }) }}
  </Alert>
  <template v-else-if="item">
    <div class="mb-6">
      <div class="mb-2 flex items-start gap-4">
        <ItemIcon :item-id="item.itemId" :size="48" :alt="item.itemName" class="mt-1" />
        <div class="flex-1">
          <PageHeader
            :title="item.itemName"
            :subtitle="`${item.homeWorldName} · ${item.dataCenter}`"
          />
          <div v-if="item.category" class="mt-1 flex flex-wrap items-center gap-2">
            <SecondaryBadge>{{ item.category }}</SecondaryBadge>
          </div>
          <p v-if="item.description" class="mt-2 max-w-3xl text-sm italic text-(--text-muted)">
            {{ item.description }}
          </p>
        </div>
      </div>
      <TabBar
        :model-value="hqMode"
        :options="[{ value: 'nq', label: 'NQ' }, { value: 'hq', label: 'HQ' }]"
        :aria-label="t('item.qualityTabs')"
        @update:model-value="hqMode = $event as 'nq' | 'hq'"
      >
        <template #tab="{ option }">
          {{ option.label }}<HQMark v-if="option.value === 'hq'" :hq="true" class="ml-1" />
        </template>
      </TabBar>
    </div>

    <div class="mb-6 grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <SellPriceCard :item="item" :sales-buckets="salesBuckets" />
      <ItemModelCard :model="item.model" />
    </div>

    <div class="mb-6">
      <ItemActionsPanel :item="item" />
    </div>

    <NeutralContainer v-if="salesBuckets.length > 0" class="mb-6">
      <SectionHeader>{{ t('item.salesHistory') }}</SectionHeader>
      <ItemTrendStrip :item-id="Number(route.params.id)" :hq="hqMode === 'hq'" class="mt-2" />
      <SalesHistoryChart :buckets="salesBuckets" :model="item.model" class="mt-3" />
    </NeutralContainer>

    <NeutralContainer>
      <SectionHeader>{{ t('item.listingsOnDc') }}</SectionHeader>
      <table v-if="item.listings.length > 0" class="mt-3 w-full text-sm">
        <THead>
          <Th>{{ t('alerts.world') }}</Th>
          <Th align="right">{{ t('item.unitPrice') }}</Th>
          <Th align="right">{{ t('offers.col.qty') }}</Th>
        </THead>
        <tbody>
          <TRow v-for="(l, i) in item.listings" :key="i">
            <Td>{{ l.worldName }}</Td>
            <Td align="right"><GilAmount :value="l.unitPrice" /></Td>
            <Td align="right">{{ l.quantity }}</Td>
          </TRow>
        </tbody>
      </table>
      <div v-else class="mt-3"><MutedText size="sm">{{ t('item.noListings') }}</MutedText></div>
    </NeutralContainer>
  </template>
</template>
