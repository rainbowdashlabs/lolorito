/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import BasketItemsTable from '@/components/ffxiv/BasketItemsTable.vue'
import BasketSidebar from '@/components/ffxiv/BasketSidebar.vue'
import { useBasket } from '@/composables/useBasket'
import { pushToast } from '@/composables/useToasts'

const { t } = useI18n()
const router = useRouter()
const basket = useBasket()

function updateQty(key: string, v: string | number | undefined | null) {
  const n = typeof v === 'string' ? Number(v) : v
  if (n == null || !Number.isFinite(n)) return
  basket.setQuantity(key, n)
}

function openPlanner() {
  router.push({ name: 'planner' })
}

function goToSavedBaskets() {
  router.push({ name: 'baskets' })
}

function clearBasket() {
  if (!window.confirm(t('basket.confirmClear'))) return
  basket.clear()
  pushToast(t('basket.clearAll'), 'info')
}
</script>

<template>
  <PageHeader :title="$t('basket.title')" :subtitle="$t('basket.subtitle')" />

  <div class="mb-4 flex flex-wrap items-center gap-3 text-sm">
    <button
      type="button"
      class="text-(--text-muted) underline hover:text-(--text)"
      @click="goToSavedBaskets"
    >
      {{ $t('basket.savedBasketsLink') }}
    </button>
  </div>

  <div class="grid gap-6 lg:grid-cols-[1fr_320px]">
    <section>
      <EmptyState v-if="basket.count.value === 0">
        {{ $t('basket.empty') }}
      </EmptyState>
      <BasketItemsTable
        v-else
        :items="basket.items.value"
        @update-qty="updateQty"
        @remove="basket.remove"
      />
    </section>

    <BasketSidebar @plan-run="openPlanner" @clear-basket="clearBasket" />
  </div>
</template>
