/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import NumberInput from '@/components/input/text/NumberInput.vue'
import { limits, list, put, CANONICAL_CLASSES } from '@/api/skills'
import type { SkillMap, SkillKind } from '@/api/skills'
import { pushToast } from '@/composables/useToasts'
import { get as loadCharacter, refresh as refreshCharacter } from '@/api/character'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

const { t } = useI18n()
const skills = ref<SkillMap>({ craft: {}, desynth: {} })
// Dynamic hint (highest catalog ilvl) — powers the Max button; never enforced.
const desynthMax = ref(999)
const loading = ref(false)
const busy = ref(false)
const linkedLodestoneId = ref<number | null>(null)
const syncing = ref(false)

async function refresh() {
  loading.value = true
  try {
    const [s, c, lim] = await Promise.all([list(), loadCharacter(), limits()])
    skills.value = s
    linkedLodestoneId.value = c?.lodestoneId ?? null
    desynthMax.value = lim.desynthMax
  } catch (e: unknown) {
    const obj = e as { message?: string }
    pushToast(t('skills.loadFailed', { error: obj?.message ?? 'unknown' }), 'error')
  } finally {
    loading.value = false
  }
}

/**
 * Re-fetch the linked character from Lodestone; the backend upserts
 * DoH job levels into the craft skill map on every successful refresh
 * so we just reload the skills afterwards.
 */
async function syncFromLodestone() {
  if (!linkedLodestoneId.value) return
  syncing.value = true
  try {
    await refreshCharacter(linkedLodestoneId.value)
    skills.value = await list()
    pushToast(t('skills.syncedFromLodestone'), 'info', 2_400)
  } catch (e: unknown) {
    const obj = e as { message?: string }
    pushToast(t('skills.saveFailed', { error: obj?.message ?? 'unknown' }), 'error')
  } finally {
    syncing.value = false
  }
}

let saveDebounce: number | undefined
function update(kind: SkillKind, className: string, level: number) {
  skills.value[kind][className] = level
  window.clearTimeout(saveDebounce)
  saveDebounce = window.setTimeout(async () => {
    busy.value = true
    try {
      skills.value = await put(kind, className, level)
      pushToast(t('skills.saved'), 'info', 1_800)
    } catch (e: unknown) {
      const obj = e as { message?: string }
      pushToast(t('skills.saveFailed', { error: obj?.message ?? 'unknown' }), 'error')
    } finally {
      busy.value = false
    }
  }, 500)
}

onMounted(refresh)
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ $t('skills.title') }}</SectionHeader>
    <MutedText size="sm">{{ $t('skills.hint') }}</MutedText>
    <div v-if="linkedLodestoneId" class="mt-3">
      <PrimaryButton :disabled="syncing || busy" :icon="['fas', 'rotate']" @click="syncFromLodestone">
        {{ syncing ? $t('skills.syncing') : $t('skills.syncFromLodestone') }}
      </PrimaryButton>
    </div>

    <div v-if="loading" class="mt-3">
      <MutedText size="sm">{{ $t('common.loading') }}</MutedText>
    </div>

    <div v-else class="mt-4 grid grid-cols-1 gap-6 md:grid-cols-2">
      <div>
        <div class="mb-2 text-sm font-semibold">{{ $t('skills.craftTitle') }}</div>
        <div class="space-y-2">
          <div
            v-for="cls in CANONICAL_CLASSES"
            :key="`c-${cls}`"
            class="flex items-center gap-3 rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) px-3 py-2"
          >
            <div class="flex-1 text-sm">{{ $t(`skills.classes.${cls}`) }}</div>
            <NumberInput
              :model-value="skills.craft[cls] ?? 0"
              :min="0"
              class="w-20"
              @update:model-value="(v) => update('craft', cls, Number(v))"
            />
          </div>
        </div>
      </div>

      <div>
        <div class="mb-2 text-sm font-semibold">{{ $t('skills.desynthTitle') }}</div>
        <div class="space-y-2">
          <div
            v-for="cls in CANONICAL_CLASSES"
            :key="`d-${cls}`"
            class="flex items-center gap-3 rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) px-3 py-2"
          >
            <div class="flex-1 text-sm">{{ $t(`skills.classes.${cls}`) }}</div>
            <NumberInput
              :model-value="skills.desynth[cls] ?? 0"
              :min="0"
              class="w-24"
              @update:model-value="(v) => update('desynth', cls, Number(v))"
            />
            <button
              type="button"
              class="text-xs text-(--text-muted) underline hover:text-(--text)"
              :title="$t('skills.maxHint', { max: desynthMax })"
              @click="update('desynth', cls, desynthMax)"
            >
              {{ $t('skills.max') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </NeutralContainer>
</template>
