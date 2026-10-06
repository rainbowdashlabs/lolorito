/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldExplainer from '@/components/typography/FieldExplainer.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NumberInput from '@/components/input/text/NumberInput.vue'
import RangeInput from '@/components/input/RangeInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PlannerPresetPicker from '@/components/ffxiv/PlannerPresetPicker.vue'
import { useWorlds } from '@/composables/useWorlds'
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PlanRequest } from '@/api/planner'

type Params = Required<Omit<PlanRequest, 'homeWorld' | 'refreshHours' | 'allowCrafts' | 'allowDesynth'>> & {
  homeWorld?: number
  refreshHours?: number
  allowCrafts?: boolean
  allowDesynth?: boolean
}

const { t } = useI18n()
const model = defineModel<Params>('params', { required: true })

defineProps<{ loading: boolean }>()
defineEmits<{ plan: [] }>()

const worlds = useWorlds()
const groupedWorlds = computed(() => {
  const groups: Array<{ label: string; worlds: Array<{ id: number; label: string }> }> = []
  for (const region of worlds.regions.value) {
    for (const dc of region.dataCenters) {
      groups.push({
        label: `${dc.name} · ${region.name}`,
        worlds: dc.worlds.map((w) => ({ id: w.id, label: w.name })),
      })
    }
  }
  return groups
})
onMounted(worlds.load)
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ t('planner.params') }}</SectionHeader>
    <PlannerPresetPicker :current="model" class="mt-4" @load="model = { ...model, ...($event as typeof model) }" />
    <div class="mt-4 space-y-4 text-sm">
      <div>
        <FieldLabel>{{ t('planner.homeWorldOverride') }}</FieldLabel>
        <SelectInput
          :model-value="String(model.homeWorld ?? '')"
          class="mt-1"
          @update:model-value="model.homeWorld = $event ? Number($event) : undefined"
        >
          <option value="">{{ t('planner.useSavedFilter') }}</option>
          <optgroup v-for="g in groupedWorlds" :key="g.label" :label="g.label">
            <option v-for="w in g.worlds" :key="w.id" :value="String(w.id)">
              {{ w.label }}
            </option>
          </optgroup>
        </SelectInput>
        <FieldExplainer>
          {{ t('planner.homeWorldOverrideHint') }}
        </FieldExplainer>
      </div>
      <div>
        <FieldLabel>{{ t('settings.freshnessWindow') }}</FieldLabel>
        <NumberInput
          :model-value="model.refreshHours ?? 6"
          :min="1"
          :max="72"
          class="mt-1"
          @update:model-value="model.refreshHours = Number($event)"
        />
        <FieldExplainer>
          {{ t('planner.freshnessHint') }}
        </FieldExplainer>
      </div>
      <div>
        <FieldLabel>{{ t('planner.budget') }}</FieldLabel>
        <NumberInput v-model="model.budget" :min="1" class="mt-1" />
        <FieldExplainer>{{ t('planner.budgetHint') }}</FieldExplainer>
      </div>
      <div>
        <FieldLabel>{{ t('planner.carrySlots') }}</FieldLabel>
        <NumberInput v-model="model.inventorySlots" :min="1" class="mt-1" />
        <FieldExplainer>
          {{ t('planner.carrySlotsHint') }}
        </FieldExplainer>
      </div>
      <div>
        <FieldLabel>{{ t('planner.attentionBudget') }}</FieldLabel>
        <NumberInput v-model="model.attentionBudgetHours" :min="0.5" :step="0.5" class="mt-1" />
        <FieldExplainer>
          {{ t('planner.attentionBudgetHint') }}
        </FieldExplainer>
      </div>
      <div>
        <FieldLabel>{{ t('planner.attentionFraction') }}</FieldLabel>
        <RangeInput
          v-model="model.attentionFraction"
          :min="0.05"
          :max="1"
          :step="0.05"
          :display="(v: number) => `${Math.round(v * 100)}%`"
          class="mt-1"
        />
        <FieldExplainer>
          {{ t('planner.attentionFractionHint') }}
        </FieldExplainer>
      </div>
      <div>
        <FieldLabel>{{ t('planner.hopWeight') }}</FieldLabel>
        <NumberInput v-model="model.hopWeightGilPerSecond" :min="0" class="mt-1" />
        <FieldExplainer>
          {{ t('planner.hopWeightHint') }}
        </FieldExplainer>
      </div>
      <div>
        <FieldLabel>{{ t('planner.maxWorlds') }}</FieldLabel>
        <NumberInput v-model="model.maxWorlds" :min="1" :max="8" class="mt-1" />
        <FieldExplainer>{{ t('planner.maxWorldsHint') }}</FieldExplainer>
      </div>
      <div>
        <FieldLabel>{{ t('planner.candidateTopK') }}</FieldLabel>
        <NumberInput v-model="model.candidateTopK" :min="10" :max="2000" class="mt-1" />
        <FieldExplainer>
          {{ t('planner.candidateTopKHint') }}
        </FieldExplainer>
      </div>
      <details class="mt-4 border-t border-(--border) pt-3">
        <summary class="cursor-pointer text-sm font-semibold">{{ t('planner.retainerOvernight') }}</summary>
        <FieldExplainer>
          {{ t('planner.retainerOvernightHint') }}
        </FieldExplainer>
        <div class="mt-3 space-y-4">
          <div>
            <FieldLabel>{{ t('planner.maxRetainerPicks') }}</FieldLabel>
            <NumberInput v-model="model.retainerSlots" :min="0" :max="200" class="mt-1" />
            <FieldExplainer>
              {{ t('planner.maxRetainerPicksHint') }}
            </FieldExplainer>
          </div>
          <div>
            <FieldLabel>{{ t('planner.retainerListings') }}</FieldLabel>
            <NumberInput v-model="model.retainerListingSlots" :min="0" :max="1000" class="mt-1" />
            <FieldExplainer>
              {{ t('planner.retainerListingsHint', { n: Math.ceil(200 / (model.retainerListingStackTarget || 20)) }) }}
            </FieldExplainer>
          </div>
          <div>
            <FieldLabel>{{ t('planner.stackTarget') }}</FieldLabel>
            <NumberInput v-model="model.retainerListingStackTarget" :min="1" :max="999" class="mt-1" />
            <FieldExplainer>
              {{ t('planner.stackTargetHint') }}
            </FieldExplainer>
          </div>
          <div>
            <FieldLabel>{{ t('planner.retainerAttention') }}</FieldLabel>
            <NumberInput
              v-model="model.retainerAttentionFraction"
              :min="0"
              :max="1"
              :step="0.01"
              class="mt-1"
            />
            <FieldExplainer>
              {{ t('planner.retainerAttentionHint') }}
            </FieldExplainer>
          </div>
          <div>
            <FieldLabel>{{ t('planner.shelfThreshold') }}</FieldLabel>
            <NumberInput
              v-model="model.retainerShelfHoursThreshold"
              :min="0"
              :max="168"
              :step="0.5"
              class="mt-1"
            />
            <FieldExplainer>
              {{ t('planner.shelfThresholdHint') }}
            </FieldExplainer>
          </div>
        </div>
      </details>
    </div>

    <div class="mt-4 rounded-(--radius-theme) border border-(--border) bg-(--bg) p-3">
      <label class="flex items-center gap-2 text-sm">
        <CheckboxInput
          :model-value="model.allowCrafts ?? false"
          @update:model-value="model.allowCrafts = $event"
        />
        {{ t('planner.allowCrafts') }}
      </label>
      <FieldExplainer>
        {{ t('planner.allowCraftsHint') }}
      </FieldExplainer>
    </div>

    <div class="mt-4 rounded-(--radius-theme) border border-(--border) bg-(--bg) p-3">
      <label class="flex items-center gap-2 text-sm">
        <CheckboxInput
          :model-value="model.allowDesynth ?? false"
          @update:model-value="model.allowDesynth = $event"
        />
        {{ t('planner.allowDesynth') }}
      </label>
      <FieldExplainer>
        {{ t('planner.allowDesynthHint') }}
      </FieldExplainer>
    </div>

    <div class="mt-4">
      <PrimaryButton full-width :disabled="loading" @click="$emit('plan')">
        <FontAwesomeIcon :icon="['fas', 'route']" class="mr-2" />
        {{ t('planner.planTheRun') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>
