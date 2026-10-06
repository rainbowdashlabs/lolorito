/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import { BORDERED_INPUT_CLASSES } from '@/components/input/inputClasses'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import { list, add, remove, suggest } from '@/api/retainers'
import type { Retainer } from '@/api/retainers'
import { useWorlds } from '@/composables/useWorlds'

const { t } = useI18n()
const worlds = useWorlds()
const retainers = ref<Retainer[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)

const newName = ref('')
const newWorld = ref<number>(0)
const suggestions = ref<string[]>([])
let suggestDebounce: number | undefined

/**
 * Regions available in the picker. SelectInput renders its slot rather
 * than taking a flat options array, so we keep the grouped structure to
 * render as native <optgroup>s below.
 */
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

function worldLabel(id: number): string {
  const info = worlds.worldById(id)
  return info.world ? `${info.world.name} · ${info.dataCenter}` : String(id)
}

async function refresh() {
  loading.value = true
  error.value = null
  try {
    retainers.value = await list()
  } catch (e: unknown) {
    const obj = e as { message?: string }
    error.value = obj?.message ?? t('common.failedToLoad', { error: '' })
  } finally {
    loading.value = false
  }
}

async function addRetainer() {
  const name = newName.value.trim()
  if (!name || newWorld.value <= 0) {
    error.value = t('retainers.validation')
    return
  }
  busy.value = true
  error.value = null
  try {
    retainers.value = await add(name, newWorld.value)
    newName.value = ''
    suggestions.value = []
  } catch (e: unknown) {
    const obj = e as { response?: { data?: { error?: string } }; message?: string }
    error.value = obj?.response?.data?.error ?? obj?.message ?? t('retainers.addFailed')
  } finally {
    busy.value = false
  }
}

async function removeRetainer(r: Retainer) {
  if (!window.confirm(t('retainers.confirmRemove', { name: r.retainerName }))) return
  busy.value = true
  try {
    await remove(r.retainerName, r.worldId)
    await refresh()
  } finally {
    busy.value = false
  }
}

async function refreshSuggestions() {
  if (newWorld.value <= 0) {
    suggestions.value = []
    return
  }
  try {
    suggestions.value = await suggest(newWorld.value, newName.value)
  } catch {
    suggestions.value = []
  }
}

// Debounce the suggestion fetch so we don't spam the backend on every keystroke.
watch([newName, newWorld], () => {
  window.clearTimeout(suggestDebounce)
  suggestDebounce = window.setTimeout(refreshSuggestions, 180)
})

onMounted(async () => {
  await worlds.load()
  await refresh()
})
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ $t('retainers.title') }}</SectionHeader>
    <MutedText size="sm">{{ $t('retainers.hint') }}</MutedText>

    <div v-if="loading" class="mt-3">
      <MutedText size="sm">{{ $t('common.loading') }}</MutedText>
    </div>

    <div v-else-if="retainers.length > 0" class="mt-3 space-y-2">
      <div
        v-for="r in retainers"
        :key="r.worldId + '-' + r.retainerName"
        class="flex flex-wrap items-center gap-3 rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) px-3 py-2"
      >
        <div class="flex-1">
          <div class="font-semibold">{{ r.retainerName }}</div>
          <MutedText size="sm">{{ worldLabel(r.worldId) }}</MutedText>
        </div>
        <SuccessBadge v-if="r.seen" :title="$t('retainers.seenTitle')">
          {{ $t('retainers.seenBadge') }}
        </SuccessBadge>
        <ErrorBadge v-else :title="$t('retainers.notSeenTitle')">
          {{ $t('retainers.notSeenBadge') }}
        </ErrorBadge>
        <SecondaryButton :disabled="busy" @click="removeRetainer(r)">
          {{ $t('retainers.remove') }}
        </SecondaryButton>
      </div>
    </div>

    <div v-else class="mt-3">
      <MutedText size="sm">{{ $t('retainers.noneDeclared') }}</MutedText>
    </div>

    <div class="mt-4 space-y-2">
      <div class="grid grid-cols-1 gap-2 sm:grid-cols-[minmax(0,1fr)_minmax(0,220px)]">
        <div>
          <FieldLabel>{{ $t('retainers.nameLabel') }}</FieldLabel>
          <!--
            Native <input> so we can pass the `list` attribute for the
            <datalist> autocomplete. TextInput wraps BaseInput which
            hides that attr, and browser autocomplete for retainer names
            is the whole point of this control.
          -->
          <input
            v-model="newName"
            type="text"
            :placeholder="$t('retainers.namePlaceholder')"
            list="retainer-suggestions"
            autocomplete="off"
            :class="['mt-1 w-full', BORDERED_INPUT_CLASSES]"
          />
          <datalist id="retainer-suggestions">
            <option v-for="s in suggestions" :key="s" :value="s" />
          </datalist>
          <MutedText v-if="newWorld > 0" size="sm">
            {{ $t('retainers.matchesOnWorld', {
              count: suggestions.length,
              world: worldLabel(newWorld),
            }, suggestions.length) }}
          </MutedText>
        </div>
        <div>
          <FieldLabel>{{ $t('retainers.worldLabel') }}</FieldLabel>
          <SelectInput
            :model-value="String(newWorld)"
            class="mt-1"
            @update:model-value="newWorld = Number($event) || 0"
          >
            <option value="0">{{ $t('retainers.pickAWorld') }}</option>
            <optgroup v-for="g in groupedWorlds" :key="g.label" :label="g.label">
              <option v-for="w in g.worlds" :key="w.id" :value="String(w.id)">
                {{ w.label }}
              </option>
            </optgroup>
          </SelectInput>
        </div>
      </div>
      <PrimaryButton :disabled="busy" @click="addRetainer">
        {{ $t('retainers.declareAction') }}
      </PrimaryButton>
      <div v-if="error" class="text-sm text-(--color-error)">{{ error }}</div>
    </div>
  </NeutralContainer>
</template>
