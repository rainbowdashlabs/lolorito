/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldExplainer from '@/components/typography/FieldExplainer.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import NumberInput from '@/components/input/text/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import { useFilter } from '@/composables/useFilter'
import { useWorlds } from '@/composables/useWorlds'
import { pushToast } from '@/composables/useToasts'
import { authApi } from '@/api'
import { ref } from 'vue'

const { t } = useI18n()
const filter = useFilter()
const worlds = useWorlds()

const homeWorldId = computed({
  get: () => filter.current.value?.worldId ?? 0,
  set: (value: number) => {
    if (value > 0) filter.patchDebounced({ worldId: value })
  },
})

const refreshHours = computed({
  get: () => filter.current.value?.refreshHours ?? 6,
  set: (value: number) => filter.patchDebounced({ refreshHours: value }),
})

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

watch(homeWorldId, (v) => {
  if (v > 0) pushToast(t('settings.homeWorldSaved'), 'info', 1_800)
})

onMounted(async () => {
  await Promise.all([worlds.load(), filter.load()])
})
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ $t('settings.defaults') }}</SectionHeader>

    <div class="mt-4 grid gap-4 sm:grid-cols-2">
      <div>
        <FieldLabel>{{ $t('settings.homeWorld') }}</FieldLabel>
        <SelectInput
          :model-value="String(homeWorldId)"
          class="mt-1"
          @update:model-value="homeWorldId = Number($event) || 0"
        >
          <option value="0">{{ $t('settings.pickAWorld') }}</option>
          <optgroup v-for="g in groupedWorlds" :key="g.label" :label="g.label">
            <option v-for="w in g.worlds" :key="w.id" :value="String(w.id)">
              {{ w.label }}
            </option>
          </optgroup>
        </SelectInput>
        <FieldExplainer>{{ $t('settings.homeWorldHint') }}</FieldExplainer>
      </div>

      <div>
        <FieldLabel>{{ $t('settings.freshnessWindow') }}</FieldLabel>
        <NumberInput
          :model-value="refreshHours"
          :min="1"
          :max="72"
          class="mt-1"
          @update:model-value="refreshHours = Number($event)"
        />
        <FieldExplainer>{{ $t('settings.freshnessWindowHint') }}</FieldExplainer>
      </div>

    </div>
  </NeutralContainer>
</template>
