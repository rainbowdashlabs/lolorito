/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import { list, save, remove } from '@/api/plannerPresets'
import type { PlannerPresetDto } from '@/api/plannerPresets'

const { t } = useI18n()
const props = defineProps<{ current: unknown }>()
const emit = defineEmits<{ load: [params: unknown] }>()

const presets = ref<PlannerPresetDto[]>([])
const selectedId = ref<string>('')
const newName = ref<string>('')
const busy = ref(false)
const flash = ref<string | null>(null)

async function refresh() {
  presets.value = await list()
}

function options() {
  return [{ value: '', label: t('planner.preset.choose') }, ...presets.value.map((p) => ({ value: p.id, label: p.name }))]
}

function onSelected(id: string) {
  selectedId.value = id
  const p = presets.value.find((x) => x.id === id)
  if (!p) return
  try {
    const parsed = JSON.parse(p.paramsJson)
    emit('load', parsed)
    flash.value = t('planner.preset.loaded', { name: p.name })
  } catch {
    flash.value = t('planner.preset.parseFailed')
  }
}

async function saveCurrent() {
  const name = newName.value.trim()
  if (!name) {
    flash.value = t('planner.preset.nameFirst')
    return
  }
  busy.value = true
  const stored = await save(name, props.current)
  busy.value = false
  if (!stored) {
    flash.value = t('planner.preset.saveFailed')
    return
  }
  await refresh()
  selectedId.value = stored.id
  newName.value = ''
  flash.value = t('planner.preset.saved', { name: stored.name })
}

async function deleteSelected() {
  if (!selectedId.value) return
  const p = presets.value.find((x) => x.id === selectedId.value)
  if (!p) return
  if (!window.confirm(t('planner.preset.confirmDelete', { name: p.name }))) return
  busy.value = true
  const ok = await remove(p.id)
  busy.value = false
  if (!ok) {
    flash.value = t('planner.preset.deleteFailed')
    return
  }
  await refresh()
  selectedId.value = ''
  flash.value = t('planner.preset.deleted', { name: p.name })
}

onMounted(refresh)
</script>

<template>
  <div class="mb-3 space-y-2 border-b border-(--border) pb-3">
    <div class="flex items-center gap-2">
      <SelectInput
        :model-value="selectedId"
        :options="options()"
        class="flex-1"
        @update:model-value="onSelected($event as string)"
      />
      <SecondaryButton :disabled="!selectedId || busy" @click="deleteSelected">{{ t('common.delete') }}</SecondaryButton>
    </div>
    <div class="flex items-center gap-2">
      <TextInput v-model="newName" :placeholder="t('planner.preset.name')" class="flex-1" />
      <PrimaryButton :disabled="busy" @click="saveCurrent">{{ t('planner.preset.saveCurrent') }}</PrimaryButton>
    </div>
    <div v-if="flash">
      <MutedText size="sm">{{ flash }}</MutedText>
    </div>
    <MutedText size="sm">
      {{ t('planner.preset.hint') }}
    </MutedText>
  </div>
</template>
