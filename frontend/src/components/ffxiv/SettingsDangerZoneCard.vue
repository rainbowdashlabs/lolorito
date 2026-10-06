/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import BaseButton from '@/components/button/BaseButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import { sessionsApi, filterApi } from '@/api'
import { useFilter } from '@/composables/useFilter'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const filter = useFilter()
const session = useSession()

type Pending = null | 'logout' | 'reset-filter'
const pending = ref<Pending>(null)
const banner = ref<string | null>(null)
const bannerVariant = ref<'success' | 'error'>('success')

function armLogout() {
  pending.value = 'logout'
}
function armReset() {
  pending.value = 'reset-filter'
}
function cancel() {
  pending.value = null
}

async function confirmLogout() {
  try {
    await sessionsApi.revokeAll()
    banner.value = t('settings.allRevoked')
    bannerVariant.value = 'success'
    setTimeout(() => (window.location.href = '/login'), 400)
  } catch (e) {
    banner.value = e instanceof Error ? e.message : String(e)
    bannerVariant.value = 'error'
  } finally {
    pending.value = null
  }
}

async function confirmReset() {
  try {
    filter.setLocal(await filterApi.reset())
    banner.value = t('settings.filterReset')
    bannerVariant.value = 'success'
  } catch (e) {
    banner.value = e instanceof Error ? e.message : String(e)
    bannerVariant.value = 'error'
  } finally {
    pending.value = null
  }
}

// Silence unused-var lint from the composable import — reserved for future
// UX polish (e.g. hiding the confirm block until identity resolves).
void session
</script>

<template>
  <ErrorContainer>
    <SectionHeader>{{ t('settings.dangerZone') }}</SectionHeader>
    <MutedText size="sm">
      {{ t('settings.dangerIntro') }}
    </MutedText>
    <Alert v-if="banner" :variant="bannerVariant" class="mt-4">{{ banner }}</Alert>

    <div class="mt-4 space-y-3">
      <div class="rounded-(--radius-theme) border border-error/40 p-3">
        <div class="font-medium">{{ t('settings.logoutAllTitle') }}</div>
        <MutedText size="sm">
          {{ t('settings.logoutAllHint') }}
        </MutedText>
        <div class="mt-3 flex flex-wrap gap-2">
          <template v-if="pending === 'logout'">
            <BaseButton
              :icon="['fas', 'arrow-right-from-bracket']"
              class="bg-error text-white hover:opacity-90"
              @click="confirmLogout"
            >
              {{ t('settings.logoutAllConfirm') }}
            </BaseButton>
            <BaseButton class="border border-(--border) bg-(--bg)" @click="cancel">{{ t('common.cancel') }}</BaseButton>
          </template>
          <BaseButton
            v-else
            :icon="['fas', 'arrow-right-from-bracket']"
            class="border border-error/60 text-error hover:bg-error/10"
            @click="armLogout"
          >
            {{ t('settings.signOutEverywhere') }}
          </BaseButton>
        </div>
      </div>

      <div class="rounded-(--radius-theme) border border-error/40 p-3">
        <div class="font-medium">{{ t('settings.resetFilterTitle') }}</div>
        <MutedText size="sm">
          {{ t('settings.resetFilterHint') }}
        </MutedText>
        <div class="mt-3 flex flex-wrap gap-2">
          <template v-if="pending === 'reset-filter'">
            <BaseButton :icon="['fas', 'trash']" class="bg-error text-white hover:opacity-90" @click="confirmReset">
              {{ t('settings.resetFilterConfirm') }}
            </BaseButton>
            <BaseButton class="border border-(--border) bg-(--bg)" @click="cancel">{{ t('common.cancel') }}</BaseButton>
          </template>
          <BaseButton
            v-else
            :icon="['fas', 'trash']"
            class="border border-error/60 text-error hover:bg-error/10"
            @click="armReset"
          >
            {{ t('settings.resetFilter') }}
          </BaseButton>
        </div>
      </div>
    </div>
  </ErrorContainer>
</template>
