/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import { get as loadCharacter, refresh as refreshCharacter, unlink as unlinkCharacter } from '@/api/character'
import type { CharacterEnvelope } from '@/api/character'

const { t } = useI18n()
const character = ref<CharacterEnvelope | null>(null)
const loading = ref(false)
const busy = ref(false)
const rawInput = ref('')
const error = ref<string | null>(null)

/**
 * Accept either the raw lodestone id or a URL like
 * {@code https://na.finalfantasyxiv.com/lodestone/character/12345678/}.
 * Returns 0 when we can't extract a positive numeric id.
 */
function parseLodestoneId(input: string): number {
  const trimmed = input.trim()
  if (!trimmed) return 0
  const digits = /(\d{5,})/.exec(trimmed)?.[1]
  if (!digits) return 0
  const parsed = Number(digits)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0
}

async function refresh() {
  loading.value = true
  error.value = null
  try {
    character.value = await loadCharacter()
    if (character.value) rawInput.value = String(character.value.lodestoneId)
  } catch (e: unknown) {
    const obj = e as { message?: string }
    error.value = obj?.message ?? t('common.failedToLoad', { error: '' })
  } finally {
    loading.value = false
  }
}

async function sync() {
  const id = parseLodestoneId(rawInput.value)
  if (id <= 0) {
    error.value = t('character.parseError')
    return
  }
  busy.value = true
  error.value = null
  try {
    character.value = await refreshCharacter(id)
  } catch (e: unknown) {
    const obj = e as { response?: { data?: { error?: string } }; message?: string }
    error.value = obj?.response?.data?.error ?? obj?.message ?? t('character.linkFailed')
  } finally {
    busy.value = false
  }
}

async function unlink() {
  if (!window.confirm(t('character.confirmUnlink'))) return
  busy.value = true
  try {
    await unlinkCharacter()
    character.value = null
    rawInput.value = ''
  } catch (e: unknown) {
    const obj = e as { message?: string }
    error.value = obj?.message ?? t('character.unlinkFailed')
  } finally {
    busy.value = false
  }
}

const staleness = computed(() => {
  if (!character.value) return ''
  return character.value.stale ? t('character.stale') : ''
})

onMounted(refresh)
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ $t('character.title') }}</SectionHeader>
    <MutedText size="sm">{{ $t('character.hint') }}</MutedText>

    <div v-if="loading" class="mt-3">
      <MutedText size="sm">{{ $t('common.loading') }}</MutedText>
    </div>

    <div v-else-if="character" class="mt-4 flex flex-wrap gap-4">
      <img
        v-if="character.profile.portraitUrl"
        :src="character.profile.portraitUrl"
        :alt="character.profile.name"
        width="880"
        height="1200"
        class="w-28 h-auto self-start object-contain rounded-(--radius-theme)"
      />
      <div class="flex-1 space-y-1">
        <div class="text-lg font-semibold">{{ character.profile.name }}</div>
        <MutedText size="sm" tag="div">
          {{ character.profile.world }}
          <template v-if="character.profile.dataCenter"> · {{ character.profile.dataCenter }}</template>
        </MutedText>
        <MutedText v-if="character.profile.freeCompany" size="sm" tag="div">
          {{ $t('character.fcPrefix', { fc: character.profile.freeCompany }) }}
        </MutedText>
        <MutedText v-if="character.profile.title" size="sm" tag="div">
          {{ character.profile.title }}
        </MutedText>
        <div class="mt-2 grid grid-cols-2 gap-2 text-sm sm:grid-cols-3">
          <div class="rounded-(--radius-theme) border border-(--border) bg-(--bg) px-3 py-2">
            <MutedText size="sm">{{ $t('character.topCrafter') }}</MutedText>
            <div class="font-semibold">
              {{ character.profile.maxCrafterLevel
                ? $t('character.lvPrefix', { v: character.profile.maxCrafterLevel })
                : $t('character.lvUnknown') }}
            </div>
          </div>
          <div class="rounded-(--radius-theme) border border-(--border) bg-(--bg) px-3 py-2">
            <MutedText size="sm">{{ $t('character.topGatherer') }}</MutedText>
            <div class="font-semibold">
              {{ character.profile.maxGathererLevel
                ? $t('character.lvPrefix', { v: character.profile.maxGathererLevel })
                : $t('character.lvUnknown') }}
            </div>
          </div>
          <div class="rounded-(--radius-theme) border border-(--border) bg-(--bg) px-3 py-2">
            <MutedText size="sm">{{ $t('character.jobsTracked') }}</MutedText>
            <div class="font-semibold">{{ Object.keys(character.profile.jobLevels).length }}</div>
          </div>
        </div>
        <MutedText size="sm">
          {{ $t('character.syncedAt', { when: character.fetchedAt.replace('T', ' ').slice(0, 16) }) }}{{ staleness }}
        </MutedText>
      </div>
    </div>

    <div v-else class="mt-3">
      <MutedText size="sm">{{ $t('character.noneLinked') }}</MutedText>
    </div>

    <div class="mt-4 space-y-2">
      <FieldLabel>{{ $t('character.idOrUrl') }}</FieldLabel>
      <TextInput v-model="rawInput" :placeholder="$t('character.placeholder')" />
      <div class="flex gap-2">
        <PrimaryButton :disabled="busy" @click="sync">
          {{ character ? $t('character.syncNow') : $t('character.linkAction') }}
        </PrimaryButton>
        <SecondaryButton v-if="character" :disabled="busy" @click="unlink">
          {{ $t('character.unlink') }}
        </SecondaryButton>
      </div>
      <div v-if="error" class="text-sm text-(--color-error)">{{ error }}</div>
    </div>
  </NeutralContainer>
</template>
