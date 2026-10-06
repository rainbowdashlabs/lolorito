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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import { list, revoke, revokeAll } from '@/api/sessions'
import type { SessionRow } from '@/api/sessions'
import { reset as resetFilter } from '@/api/filter'
import { pushToast } from '@/composables/useToasts'
import { useFilter } from '@/composables/useFilter'

const { t } = useI18n()
const filter = useFilter()
const sessions = ref<SessionRow[]>([])
const loading = ref(false)
const busy = ref(false)

async function refresh() {
  loading.value = true
  try {
    sessions.value = await list()
  } catch (e: unknown) {
    const obj = e as { message?: string }
    pushToast(t('settings.sessionsLoadFailed', { error: obj?.message ?? 'unknown error' }), 'error')
  } finally {
    loading.value = false
  }
}

async function revokeOne(row: SessionRow) {
  if (row.current) {
    if (!window.confirm(t('settings.confirmRevokeThis'))) return
  } else if (!window.confirm(t('settings.confirmRevokeOne'))) return
  busy.value = true
  try {
    await revoke(row.id)
    pushToast(t('settings.sessionRevoked'), 'success')
    if (row.current) {
      window.location.href = '/'
      return
    }
    await refresh()
  } catch (e: unknown) {
    const obj = e as { message?: string }
    pushToast(t('settings.sessionRevokeFailed', { error: obj?.message ?? 'unknown' }), 'error')
  } finally {
    busy.value = false
  }
}

async function revokeEverything() {
  if (!window.confirm(t('settings.confirmRevokeAll'))) return
  busy.value = true
  try {
    const { removed } = await revokeAll()
    pushToast(t('settings.sessionsRevokedCount', { count: removed }), 'success')
    window.location.href = '/'
  } catch (e: unknown) {
    const obj = e as { message?: string }
    pushToast(t('settings.sessionRevokeFailed', { error: obj?.message ?? 'unknown' }), 'error')
  } finally {
    busy.value = false
  }
}

async function resetPreferences() {
  if (!window.confirm(t('settings.confirmResetFilter'))) return
  busy.value = true
  try {
    const next = await resetFilter()
    filter.setLocal(next)
    pushToast(t('settings.filterReset'), 'success')
  } catch (e: unknown) {
    const obj = e as { message?: string }
    pushToast(t('settings.filterResetFailed', { error: obj?.message ?? 'unknown' }), 'error')
  } finally {
    busy.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ $t('settings.sessionsTitle') }}</SectionHeader>
    <MutedText size="sm">{{ $t('settings.sessionsHint') }}</MutedText>

    <div v-if="loading" class="mt-3">
      <MutedText size="sm">{{ $t('common.loading') }}</MutedText>
    </div>

    <div v-else-if="sessions.length > 0" class="mt-3 space-y-2">
      <div
        v-for="s in sessions"
        :key="s.id"
        class="flex flex-wrap items-center gap-3 rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) px-3 py-2"
      >
        <div class="flex-1">
          <div class="text-sm font-semibold">{{ s.userAgent || $t('settings.unknownClient') }}</div>
          <MutedText size="sm">
            {{ $t('settings.sessionCreated', {
              when: s.createdAt.slice(0, 16).replace('T', ' '),
              expires: s.expiresAt.slice(0, 10),
            }) }}
          </MutedText>
        </div>
        <SuccessBadge v-if="s.current">{{ $t('settings.thisSession') }}</SuccessBadge>
        <SecondaryButton :disabled="busy" @click="revokeOne(s)">{{ $t('settings.revoke') }}</SecondaryButton>
      </div>
    </div>

    <div v-else class="mt-3">
      <MutedText size="sm">{{ $t('settings.noSessions') }}</MutedText>
    </div>
  </NeutralContainer>
</template>
