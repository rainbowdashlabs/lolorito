/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NumberInput from '@/components/input/text/NumberInput.vue'
import ItemPicker from '@/components/ffxiv/ItemPicker.vue'
import ShoppingPlanResult from '@/components/ffxiv/ShoppingPlanResult.vue'
import { shoppingApi } from '@/api'
import type { ShoppingDecision, ShoppingPlan } from '@/api/shopping'
import type { ItemSearchHit } from '@/api/itemSearch'

const { t } = useI18n()

const pickedItem = ref<ItemSearchHit | null>(null)
const count = ref<number | undefined>(1)
const overrides = ref<Record<number, ShoppingDecision>>({})
const hqItemIds = ref<Set<number>>(new Set())

const plan = ref<ShoppingPlan | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const noRecipe = ref(false)

async function replan() {
  const item = pickedItem.value
  if (!item) return
  loading.value = true
  error.value = null
  noRecipe.value = false
  try {
    plan.value = await shoppingApi.plan({
      itemId: item.itemId,
      count: Math.max(1, Math.round(count.value ?? 1)),
      overrides: overrides.value,
      hqItemIds: [...hqItemIds.value],
    })
  } catch (e: unknown) {
    const obj = e as { response?: { status?: number; data?: { error?: string } }; message?: string }
    if (obj?.response?.status === 404) noRecipe.value = true
    else error.value = obj?.response?.data?.error ?? obj?.message ?? 'error'
    plan.value = null
  } finally {
    loading.value = false
  }
}

// New product: the per-item choices belong to the old tree — reset them.
watch(pickedItem, () => {
  overrides.value = {}
  hqItemIds.value = new Set()
  plan.value = null
  void replan()
})

let countDebounce: ReturnType<typeof setTimeout> | null = null
watch(count, () => {
  if (countDebounce) clearTimeout(countDebounce)
  countDebounce = setTimeout(() => void replan(), 300)
})

function toggleDecision(itemId: number, decision: ShoppingDecision) {
  overrides.value = { ...overrides.value, [itemId]: decision }
  void replan()
}

function toggleHq(itemId: number, hq: boolean) {
  const next = new Set(hqItemIds.value)
  if (hq) next.add(itemId)
  else next.delete(itemId)
  hqItemIds.value = next
  void replan()
}
</script>

<template>
  <PageHeader :title="t('shopping.title')" :subtitle="t('shopping.subtitle')" />

  <NeutralContainer class="mb-4">
    <div class="flex flex-wrap items-end gap-3">
      <div class="min-w-64 flex-1">
        <FieldLabel class="mb-1">{{ t('shopping.itemLabel') }}</FieldLabel>
        <ItemPicker v-model="pickedItem" />
      </div>
      <div class="w-28">
        <FieldLabel class="mb-1">{{ t('shopping.countLabel') }}</FieldLabel>
        <NumberInput v-model="count" :min="1" step="1" />
        <MutedText size="xs" class="mt-1 block">{{ t('shopping.countHint') }}</MutedText>
      </div>
    </div>
  </NeutralContainer>

  <Alert v-if="error" variant="error" class="mb-4">
    {{ t('shopping.failed', { error }) }}
  </Alert>
  <Alert v-else-if="noRecipe" variant="info" class="mb-4">
    {{ t('shopping.noRecipe') }}
  </Alert>

  <div v-if="loading && !plan" class="py-8 text-center">
    <Spinner size="lg" />
    <div class="mt-2"><MutedText size="sm">{{ t('common.loading') }}</MutedText></div>
  </div>

  <ShoppingPlanResult
    v-else-if="plan"
    :plan="plan"
    :hq-item-ids="hqItemIds"
    :class="loading ? 'pointer-events-none opacity-60' : ''"
    @toggle-decision="toggleDecision"
    @toggle-hq="toggleHq"
  />

  <EmptyState v-else-if="!error && !noRecipe">
    <FontAwesomeIcon :icon="['fas', 'cart-shopping']" class="mb-2 text-3xl" />
    <div>{{ t('shopping.empty') }}</div>
  </EmptyState>
</template>
