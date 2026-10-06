/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import DesynthTable from '@/components/ffxiv/DesynthTable.vue'
import BaseInput from '@/components/input/BaseInput.vue'
import ToggleSwitch from '@/components/input/toggle/ToggleSwitch.vue'
import { list } from '@/api/desynth'
import type { DesynthCandidate } from '@/api/desynth'

const CANONICAL_CLASSES = [
  'carpenter',
  'blacksmith',
  'armorer',
  'goldsmith',
  'leatherworker',
  'weaver',
  'alchemist',
  'culinarian',
] as const

const { t } = useI18n()
const router = useRouter()
const items = ref<DesynthCandidate[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const selectedClasses = ref(new Set<string>())
const minLevel = ref(0)
const scope = ref<'dc' | 'region'>('dc')

const activeClasses = computed(() => [...selectedClasses.value])

async function refresh() {
  loading.value = true
  error.value = null
  try {
    items.value = await list({
      classes: activeClasses.value,
      minLevel: minLevel.value,
      scope: scope.value,
    })
  } catch (e: unknown) {
    const obj = e as { response?: { data?: { error?: string } }; message?: string }
    error.value = obj?.response?.data?.error ?? obj?.message ?? 'error'
  } finally {
    loading.value = false
  }
}

function toggleClass(cls: string) {
  if (selectedClasses.value.has(cls)) selectedClasses.value.delete(cls)
  else selectedClasses.value.add(cls)
  selectedClasses.value = new Set(selectedClasses.value)
}

function resetFilters() {
  selectedClasses.value = new Set()
  minLevel.value = 0
  scope.value = 'dc'
}

function open(row: DesynthCandidate) {
  router.push({ name: 'item', params: { id: String(row.itemId) } })
}

watch([activeClasses, minLevel, scope], refresh)
onMounted(refresh)
</script>

<template>
  <PageHeader :title="$t('desynth.title')" :subtitle="$t('desynth.subtitle')" />

  <NeutralContainer class="mb-4 space-y-3">
    <div>
      <div class="mb-1 text-sm font-medium">{{ t('desynth.class') }}</div>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="cls in CANONICAL_CLASSES"
          :key="cls"
          type="button"
          :class="[
            'inline-flex items-center rounded-theme border px-2 py-1 text-xs capitalize transition-all duration-150 active:scale-95',
            selectedClasses.has(cls)
              ? 'border-(--border-strong) bg-(--bg-accent-strong) font-semibold'
              : 'border-(--border) bg-(--bg-accent) hover:bg-(--bg)',
          ]"
          @click="toggleClass(cls)"
        >
          {{ cls }}
        </button>
      </div>
      <MutedText size="xs" class="mt-1 block">{{ t('desynth.classHint') }}</MutedText>
    </div>
    <div class="flex flex-wrap items-end gap-3">
      <label class="flex flex-col gap-1">
        <span class="text-sm font-medium">{{ t('desynth.minLevel') }}</span>
        <BaseInput v-model.number="minLevel" type="number" step="1" class="w-24" />
        <MutedText size="xs">{{ t('desynth.minLevelHint') }}</MutedText>
      </label>
      <div class="flex flex-col gap-1">
        <span class="text-sm font-medium">{{ t('desynth.scope') }}</span>
        <ToggleSwitch
          v-model="scope"
          option-a="dc"
          option-b="region"
          :label-a="t('desynth.scopeDc')"
          :label-b="t('desynth.scopeRegion')"
        />
        <MutedText size="xs">{{ t('desynth.scopeHint') }}</MutedText>
      </div>
      <button
        type="button"
        class="text-xs text-(--text-muted) underline hover:text-(--text)"
        @click="resetFilters"
      >
        {{ t('desynth.reset') }}
      </button>
    </div>
  </NeutralContainer>

  <div v-if="loading" class="py-8 text-center">
    <Spinner size="lg" />
    <div class="mt-2"><MutedText size="sm">{{ $t('common.loading') }}</MutedText></div>
  </div>
  <Alert v-else-if="error" variant="error">{{ error }}</Alert>
  <NeutralContainer v-else-if="items.length > 0">
    <DesynthTable :items="items" @open="open" />
  </NeutralContainer>
  <NeutralContainer v-else>
    <MutedText size="sm">{{ $t('desynth.empty') }}</MutedText>
  </NeutralContainer>
</template>
