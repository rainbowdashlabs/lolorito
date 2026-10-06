/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ItemPicker from '@/components/ffxiv/ItemPicker.vue'
import type { ItemSearchHit } from '@/api/itemSearch'
import { useWorlds } from '@/composables/useWorlds'
import type { AlertKind, CreateRequest } from '@/api/alerts'

type ScopeMode = 'world' | 'dataCenter'
type HqChoice = 'any' | 'hq' | 'nq'

const emit = defineEmits<{ submit: [payload: CreateRequest] }>()
const props = defineProps<{ submitting: boolean; error: string | null }>()

const { t } = useI18n()
const worlds = useWorlds()

const form = ref({
  itemId: 0,
  scopeMode: 'world' as ScopeMode,
  worldId: 0,
  dataCenterId: 0,
  hq: 'any' as HqChoice,
  kind: 'price_below' as AlertKind,
  thresholdPrice: 10_000,
  cooldownMinutes: 60,
})

const pickedItem = ref<ItemSearchHit | null>(null)
watch(pickedItem, (hit) => {
  form.value.itemId = hit?.itemId ?? 0
})

const dataCenters = computed(() => {
  const seen = new Map<number, string>()
  for (const region of worlds.regions.value) {
    for (const dc of region.dataCenters) seen.set(dc.id, dc.name)
  }
  return [...seen.entries()].map(([id, name]) => ({ id, name }))
})

const allWorlds = computed(() => {
  const list: { id: number; name: string }[] = []
  for (const region of worlds.regions.value) {
    for (const dc of region.dataCenters) {
      for (const w of dc.worlds) list.push({ id: w.id, name: w.name })
    }
  }
  return list
})

function submit() {
  const hq = form.value.hq === 'any' ? null : form.value.hq === 'hq'
  emit('submit', {
    itemId: form.value.itemId,
    worldId: form.value.scopeMode === 'world' ? form.value.worldId || null : null,
    dataCenterId: form.value.scopeMode === 'dataCenter' ? form.value.dataCenterId || null : null,
    hq,
    kind: form.value.kind,
    thresholdPrice: form.value.thresholdPrice,
    cooldownMinutes: form.value.cooldownMinutes,
  })
}
</script>

<template>
  <section class="rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) p-4">
    <SectionHeader>{{ t('alerts.newAlert') }}</SectionHeader>
    <div class="mt-3 space-y-3 text-sm">
      <div>
        <label class="mb-1 block text-xs font-semibold uppercase tracking-wider text-(--text-muted)">{{ t('alerts.item') }}</label>
        <ItemPicker v-model="pickedItem" />
      </div>
      <div>
        <label class="mb-1 block text-xs font-semibold uppercase tracking-wider text-(--text-muted)">{{ t('alerts.scope') }}</label>
        <SelectInput v-model="form.scopeMode">
          <option value="world">{{ t('alerts.scopeWorld') }}</option>
          <option value="dataCenter">{{ t('alerts.scopeDataCenter') }}</option>
        </SelectInput>
      </div>
      <div v-if="form.scopeMode === 'world'">
        <label class="mb-1 block text-xs font-semibold uppercase tracking-wider text-(--text-muted)">{{ t('alerts.world') }}</label>
        <SelectInput v-model.number="form.worldId">
          <option :value="0">{{ t('alerts.pickOne') }}</option>
          <option v-for="w in allWorlds" :key="w.id" :value="w.id">{{ w.name }}</option>
        </SelectInput>
      </div>
      <div v-else>
        <label class="mb-1 block text-xs font-semibold uppercase tracking-wider text-(--text-muted)">{{ t('alerts.dataCenter') }}</label>
        <SelectInput v-model.number="form.dataCenterId">
          <option :value="0">{{ t('alerts.pickOne') }}</option>
          <option v-for="dc in dataCenters" :key="dc.id" :value="dc.id">{{ dc.name }}</option>
        </SelectInput>
      </div>
      <div>
        <label class="mb-1 block text-xs font-semibold uppercase tracking-wider text-(--text-muted)">{{ t('alerts.quality') }}</label>
        <SelectInput v-model="form.hq">
          <option value="any">{{ t('alerts.qualityEither') }}</option>
          <option value="hq">{{ t('alerts.qualityHq') }}</option>
          <option value="nq">{{ t('alerts.qualityNq') }}</option>
        </SelectInput>
      </div>
      <div>
        <label class="mb-1 block text-xs font-semibold uppercase tracking-wider text-(--text-muted)">{{ t('alerts.trigger') }}</label>
        <SelectInput v-model="form.kind">
          <option value="price_below">{{ t('alerts.triggerBelow') }}</option>
          <option value="price_above">{{ t('alerts.triggerAbove') }}</option>
        </SelectInput>
      </div>
      <div>
        <label class="mb-1 block text-xs font-semibold uppercase tracking-wider text-(--text-muted)">{{ t('alerts.threshold') }}</label>
        <input
          v-model.number="form.thresholdPrice"
          type="number"
          min="1"
          class="w-full rounded-(--radius-theme) border border-(--border) bg-(--bg) px-2 py-1"
        />
      </div>
      <div>
        <label class="mb-1 block text-xs font-semibold uppercase tracking-wider text-(--text-muted)">{{ t('alerts.cooldown') }}</label>
        <input
          v-model.number="form.cooldownMinutes"
          type="number"
          min="0"
          class="w-full rounded-(--radius-theme) border border-(--border) bg-(--bg) px-2 py-1"
        />
      </div>
      <Alert v-if="props.error" variant="error">{{ props.error }}</Alert>
      <PrimaryButton :disabled="props.submitting || form.itemId <= 0" full-width @click="submit">
        <span v-if="props.submitting">{{ t('alerts.creating') }}</span>
        <span v-else>{{ t('alerts.createAlert') }}</span>
      </PrimaryButton>
    </div>
  </section>
</template>
