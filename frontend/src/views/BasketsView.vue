/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import { basketsApi } from '@/api'
import type { Basket } from '@/api/baskets'
import { useBasket } from '@/composables/useBasket'

const { t } = useI18n()
const router = useRouter()
const basket = useBasket()
const baskets = ref<Basket[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

async function refresh() {
  loading.value = true
  error.value = null
  try {
    baskets.value = await basketsApi.list()
  } catch (e: unknown) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

function extractError(e: unknown): string {
  const obj = e as { response?: { data?: { error?: string } }; message?: string }
  return obj?.response?.data?.error ?? obj?.message ?? String(e)
}

function shareUrl(token: string): string {
  return `${window.location.origin}/baskets/shared/${token}`
}

async function copyShare(token: string) {
  try {
    await navigator.clipboard.writeText(shareUrl(token))
  } catch {
    // Silent — the raw URL is still visible in the DOM.
  }
}

/** Replace the local basket with the saved one and open the drawer. */
function loadIntoLocal(b: Basket) {
  basket.clear()
  for (const item of b.items) {
    basket.add({
      itemId: item.itemId,
      itemName: item.itemName,
      hq: item.hq,
      sourceWorldId: item.sourceWorldId,
      sourceWorldName: item.sourceWorldName,
      quantity: item.quantity,
      buyPrice: item.buyPrice,
      action: item.action,
      evPerHour: item.evPerHour,
    })
  }
  router.push({ name: 'planner' })
}

async function remove(id: string) {
  try {
    await basketsApi.remove(id)
    baskets.value = baskets.value.filter((b) => b.id !== id)
  } catch (e: unknown) {
    error.value = extractError(e)
  }
}

onMounted(refresh)
</script>

<template>
  <PageHeader :title="t('baskets.title')" :subtitle="t('baskets.subtitle')" />

  <div class="mb-4 flex flex-wrap items-center gap-3 text-sm">
    <button
      type="button"
      class="text-(--text-muted) underline hover:text-(--text)"
      @click="router.push({ name: 'basket' })"
    >
      {{ $t('basket.workingBasketLink') }}
    </button>
  </div>

  <div v-if="loading" class="py-8 text-center">
    <Spinner size="lg" />
    <div class="mt-2"><MutedText size="sm">{{ t('baskets.loading') }}</MutedText></div>
  </div>
  <Alert v-else-if="error" variant="error">{{ t('common.failedToLoad', { error }) }}</Alert>
  <EmptyState v-else-if="baskets.length === 0">
    {{ t('baskets.empty') }}
  </EmptyState>
  <ul v-else class="space-y-3">
    <li
      v-for="b in baskets"
      :key="b.id"
      class="rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) p-4"
    >
      <div class="flex flex-wrap items-start gap-3">
        <div class="min-w-0 flex-1">
          <div class="font-semibold">{{ b.name }}</div>
          <MutedText size="sm">
            {{ t('baskets.itemCount', { count: b.items.length }, b.items.length) }} · {{ t('baskets.updatedUtc', { when: b.updatedAt.slice(0, 16).replace('T', ' ') }) }}
          </MutedText>
          <div class="mt-1">
            <MutedText size="sm">{{ t('baskets.visibility') }}</MutedText> <span class="font-mono text-sm">{{ b.visibility }}</span>
          </div>
        </div>
        <div class="flex gap-2">
          <PrimaryButton compact @click="loadIntoLocal(b)">{{ t('baskets.load') }}</PrimaryButton>
          <DeleteButton @click="remove(b.id)" />
        </div>
      </div>
      <div v-if="b.visibility !== 'private'" class="mt-3 flex flex-wrap items-center gap-2">
        <MutedText size="sm">{{ t('baskets.shareLink') }}</MutedText>
        <code class="rounded bg-(--bg) px-2 py-1 text-xs">{{ shareUrl(b.shareToken) }}</code>
        <button
          type="button"
          class="rounded-(--radius-theme) border border-(--border) px-2 py-1 text-xs hover:bg-(--bg)"
          @click="copyShare(b.shareToken)"
        >
          {{ t('common.copy') }}
        </button>
      </div>
    </li>
  </ul>
</template>
