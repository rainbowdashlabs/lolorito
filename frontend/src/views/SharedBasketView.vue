/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import WorldBadge from '@/components/ffxiv/WorldBadge.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import { basketsApi } from '@/api'
import type { SharedBasket } from '@/api/baskets'
import { useBasket, type BasketLoadMode } from '@/composables/useBasket'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const basket = useBasket()
const session = useSession()

const shared = ref<SharedBasket | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

async function refresh() {
  loading.value = true
  error.value = null
  try {
    const token = String(route.params.token)
    shared.value = await basketsApi.getShared(token)
  } catch (e: unknown) {
    const status = (e as { response?: { status?: number } })?.response?.status
    if (status === 404) {
      error.value = t('baskets.notFound')
    } else {
      const obj = e as { response?: { data?: { error?: string } }; message?: string }
      error.value = obj?.response?.data?.error ?? obj?.message ?? String(e)
    }
  } finally {
    loading.value = false
  }
}

function loadIntoLocal(mode: BasketLoadMode) {
  if (!shared.value) return
  basket.load(shared.value.items, mode)
  router.push({ name: 'planner' })
}

onMounted(refresh)
</script>

<template>
  <PageHeader :title="t('baskets.sharedTitle')" :subtitle="t('baskets.sharedSubtitle')" />

  <div v-if="loading" class="py-8 text-center">
    <Spinner size="lg" />
  </div>
  <Alert v-else-if="error" variant="error">
    {{ error }}
    <div v-if="!session.isAuthenticated.value" class="mt-2">
      <MutedText size="sm">{{ t('baskets.signedInHint') }}</MutedText>
    </div>
  </Alert>
  <div v-else-if="shared">
    <div class="rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) p-4">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div class="text-lg font-semibold">{{ shared.name }}</div>
          <MutedText size="sm">
            {{ t('baskets.itemCount', { count: shared.items.length }, shared.items.length) }} · {{ t('baskets.updatedUtc', { when: shared.updatedAt.slice(0, 16).replace('T', ' ') }) }}
          </MutedText>
        </div>
        <div class="flex flex-wrap gap-2">
          <PrimaryButton @click="loadIntoLocal('replace')">
            <FontAwesomeIcon :icon="['fas', 'basket-shopping']" class="mr-2" />
            {{ basket.count.value > 0 ? t('baskets.loadReplace') : t('baskets.loadIntoBasket') }}
          </PrimaryButton>
          <SecondaryButton v-if="basket.count.value > 0" @click="loadIntoLocal('merge')">
            {{ t('baskets.loadMerge') }}
          </SecondaryButton>
        </div>
      </div>
    </div>

    <ul class="mt-4 space-y-3">
      <li
        v-for="item in shared.items"
        :key="item.key"
        class="rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) p-3"
      >
        <div class="flex items-start gap-3">
          <ItemIcon :item-id="item.itemId" :size="32" :alt="item.itemName" />
          <div class="min-w-0 flex-1">
            <div class="font-medium">{{ item.itemName }}<HQMark :hq="item.hq" /></div>
            <div class="mt-1"><WorldBadge :world-name="item.sourceWorldName" /></div>
            <div class="mt-1 text-sm">
              {{ item.quantity }} × <GilAmount :value="item.buyPrice" />
              <MutedText size="sm" class="ml-1">· {{ item.action }}</MutedText>
            </div>
          </div>
        </div>
      </li>
    </ul>
  </div>
</template>
