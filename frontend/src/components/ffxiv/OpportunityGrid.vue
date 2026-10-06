/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import WorldBadge from '@/components/ffxiv/WorldBadge.vue'
import { offersApi } from '@/api'
import type { ScoredOffer } from '@/api/offers'
import { useWorlds } from '@/composables/useWorlds'
import { formatHours } from '@/util/format'

const { t } = useI18n()
const router = useRouter()
const worlds = useWorlds()
const items = ref<ScoredOffer[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

async function refresh() {
  loading.value = true
  error.value = null
  try {
    const res = await offersApi.list({ limit: 6 })
    items.value = res.offers.slice(0, 6)
  } catch (e: unknown) {
    const obj = e as { message?: string }
    error.value = obj?.message ?? 'error'
  } finally {
    loading.value = false
  }
}

function worldName(id: number): string {
  return worlds.worldById(id).world?.name ?? String(id)
}

function open(row: ScoredOffer) {
  router.push({ name: 'item', params: { id: String(row.itemId) }, query: { hq: row.hq ? '1' : '0' } })
}

onMounted(async () => {
  await worlds.load()
  await refresh()
})
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ $t('dashboard.opportunities') }}</SectionHeader>
    <MutedText size="sm">{{ $t('dashboard.opportunitiesHint') }}</MutedText>

    <div v-if="loading" class="mt-4">
      <MutedText size="sm">{{ $t('common.loading') }}</MutedText>
    </div>
    <div v-else-if="error" class="mt-4">
      <MutedText size="sm">{{ error }}</MutedText>
    </div>
    <div v-else-if="items.length === 0" class="mt-4">
      <MutedText size="sm">{{ $t('dashboard.opportunitiesEmpty') }}</MutedText>
    </div>
    <div v-else class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <button
        v-for="row in items"
        :key="`${row.sourceWorldId}-${row.itemId}-${row.hq}`"
        type="button"
        class="rounded-(--radius-theme) border border-(--border) bg-(--bg) p-3 text-left transition hover:bg-(--bg-accent)"
        @click="open(row)"
      >
        <div class="flex items-center gap-2">
          <ItemIcon :item-id="row.itemId" :size="28" :alt="row.itemName" />
          <div class="min-w-0 flex-1">
            <div class="truncate font-semibold">{{ row.itemName }} <HQMark :hq="row.hq" /></div>
            <MutedText size="sm">{{ worldName(row.sourceWorldId) }}</MutedText>
          </div>
        </div>
        <div class="mt-2 grid grid-cols-3 gap-2 text-xs">
          <div>
            <MutedText size="sm">{{ t('offers.col.buy') }}</MutedText>
            <div class="font-semibold"><GilAmount :value="row.buyPrice" /></div>
          </div>
          <div>
            <MutedText size="sm">{{ t('offers.col.gilPerHour') }}</MutedText>
            <div class="font-semibold text-(--color-success)"><GilAmount :value="row.valuation.evPerHour" /></div>
          </div>
          <div>
            <MutedText size="sm">{{ t('offers.col.expectedShelf') }}</MutedText>
            <div class="font-semibold">{{ formatHours(row.valuation.expectedTimeOnShelfHours) }}</div>
          </div>
        </div>
      </button>
    </div>
  </NeutralContainer>
</template>
