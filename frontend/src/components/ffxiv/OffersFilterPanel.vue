/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldExplainer from '@/components/typography/FieldExplainer.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/text/NumberInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'

/**
 * Client-side filter state layered on top of the offers list. The
 * home-world / freshness / scope bits are persisted server-side via
 * the existing filter endpoint; the client-only knobs (min EV/hr, min
 * shelf class, hide bot / ghost / thin) live only in the current
 * session and gate rendering.
 */
export interface OffersClientFilter {
  minEvPerHour: number
  minShelf: 'any' | 'lt-1h' | 'lt-1d' | 'overnight-ok'
  hideBot: boolean
  hideGhost: boolean
  hideInsufficient: boolean
}

const { t } = useI18n()
const clientFilter = defineModel<OffersClientFilter>('clientFilter', { required: true })
defineProps<{
  homeWorldId: number | null | undefined
  refreshHours: number | undefined
  scope: 'DATA_CENTER' | 'REGION'
  homeWorldOptions: Array<{ id: number; label: string }>
  budget: number
  inventorySlots: number
}>()
defineEmits<{
  worldChange: [value: string | number | null | undefined]
  refreshHoursChange: [value: string | number | null | undefined]
  scopeChange: [value: 'DATA_CENTER' | 'REGION']
  boundsChange: [value: { budget?: number; inventorySlots?: number }]
}>()
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ t('offersFilter.filter') }}</SectionHeader>
    <div class="mt-4 space-y-4">
      <div>
        <FieldLabel>{{ t('settings.homeWorld') }}</FieldLabel>
        <SelectInput
          :model-value="homeWorldId ?? null"
          class="mt-1 w-full"
          @update:model-value="$emit('worldChange', $event)"
        >
          <option :value="null" disabled>{{ t('offersFilter.selectAWorld') }}</option>
          <option v-for="o in homeWorldOptions" :key="o.id" :value="o.id">{{ o.label }}</option>
        </SelectInput>
      </div>
      <div>
        <FieldLabel>{{ t('offersFilter.scope') }}</FieldLabel>
        <SelectInput
          :model-value="scope"
          class="mt-1 w-full"
          @update:model-value="$emit('scopeChange', $event as 'DATA_CENTER' | 'REGION')"
        >
          <option value="DATA_CENTER">{{ t('offersFilter.scopeDataCenter') }}</option>
          <option value="REGION">{{ t('offersFilter.scopeRegion') }}</option>
        </SelectInput>
        <FieldExplainer>
          {{ t('offersFilter.scopeHint') }}
        </FieldExplainer>
      </div>
      <div>
        <FieldLabel>{{ t('offersFilter.listingFreshness') }}</FieldLabel>
        <SelectInput
          :model-value="refreshHours ?? 6"
          class="mt-1 w-full"
          @update:model-value="$emit('refreshHoursChange', $event)"
        >
          <option :value="1">{{ t('offersFilter.lastHour') }}</option>
          <option :value="3">{{ t('offersFilter.lastNHours', { n: 3 }) }}</option>
          <option :value="6">{{ t('offersFilter.lastNHours', { n: 6 }) }}</option>
          <option :value="12">{{ t('offersFilter.lastNHours', { n: 12 }) }}</option>
          <option :value="24">{{ t('offersFilter.lastDay') }}</option>
        </SelectInput>
      </div>
      <div class="grid grid-cols-2 gap-2">
        <div>
          <FieldLabel>{{ t('offersFilter.budget') }}</FieldLabel>
          <NumberInput
            :model-value="budget || undefined"
            :min="0"
            :step="10000"
            :placeholder="t('offersFilter.unbounded')"
            class="mt-1 w-full"
            @update:model-value="$emit('boundsChange', { budget: Math.max(0, Math.floor($event ?? 0)) })"
          />
        </div>
        <div>
          <FieldLabel>{{ t('offersFilter.inventorySlots') }}</FieldLabel>
          <NumberInput
            :model-value="inventorySlots || undefined"
            :min="0"
            :max="140"
            :placeholder="t('offersFilter.unbounded')"
            class="mt-1 w-full"
            @update:model-value="$emit('boundsChange', { inventorySlots: Math.max(0, Math.floor($event ?? 0)) })"
          />
        </div>
        <FieldExplainer class="col-span-2">
          {{ t('offersFilter.boundsHint') }}
        </FieldExplainer>
      </div>
      <div>
        <FieldLabel>{{ t('offersFilter.minEvPerHour') }}</FieldLabel>
        <NumberInput
          v-model="clientFilter.minEvPerHour"
          :min="0"
          :step="1000"
          class="mt-1 w-full"
        />
        <FieldExplainer>
          {{ t('offersFilter.minEvHint') }}
        </FieldExplainer>
      </div>
      <div>
        <FieldLabel>{{ t('offersFilter.shelfClass') }}</FieldLabel>
        <SelectInput v-model="clientFilter.minShelf" class="mt-1 w-full">
          <option value="any">{{ t('offersFilter.shelfAny') }}</option>
          <option value="lt-1h">{{ t('offersFilter.shelfLt1h') }}</option>
          <option value="lt-1d">{{ t('offersFilter.shelfLt1d') }}</option>
          <option value="overnight-ok">{{ t('offersFilter.shelfOvernight') }}</option>
        </SelectInput>
        <FieldExplainer>
          {{ t('offersFilter.shelfHint') }}
        </FieldExplainer>
      </div>
      <div class="space-y-2 border-t border-(--border) pt-3">
        <label class="flex items-center gap-2 text-sm">
          <CheckboxInput v-model="clientFilter.hideBot" />
          {{ t('offersFilter.hideBot') }}
        </label>
        <label class="flex items-center gap-2 text-sm">
          <CheckboxInput v-model="clientFilter.hideGhost" />
          {{ t('offersFilter.hideGhost') }}
        </label>
        <label class="flex items-center gap-2 text-sm">
          <CheckboxInput v-model="clientFilter.hideInsufficient" />
          {{ t('offersFilter.hideThin') }}
        </label>
        <FieldExplainer>
          {{ t('offersFilter.flagsHint') }}
        </FieldExplainer>
      </div>
    </div>
  </NeutralContainer>
</template>
