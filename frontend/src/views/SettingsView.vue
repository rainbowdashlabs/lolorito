/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import CharacterCard from '@/components/ffxiv/CharacterCard.vue'
import RetainerListCard from '@/components/ffxiv/RetainerListCard.vue'
import SessionListCard from '@/components/ffxiv/SessionListCard.vue'
import DefaultPreferencesCard from '@/components/ffxiv/DefaultPreferencesCard.vue'
import SkillLevelsCard from '@/components/ffxiv/SkillLevelsCard.vue'
import AlertWebhookCard from '@/components/ffxiv/AlertWebhookCard.vue'
import SettingsDangerZoneCard from '@/components/ffxiv/SettingsDangerZoneCard.vue'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const session = useSession()
</script>

<template>
  <PageHeader :title="t('settings.title')" :subtitle="t('settings.subtitle')" />

  <div class="rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) p-4">
    <div class="mb-2 text-sm font-semibold uppercase tracking-wider text-(--text-muted)">
      {{ t('common.signedInAs') }}
    </div>
    <div v-if="session.user.value" class="flex items-center gap-4">
      <img
        v-if="session.user.value.avatarUrl"
        :src="session.user.value.avatarUrl"
        :alt="session.user.value.displayName"
        class="h-14 w-14 rounded-full"
      />
      <div>
        <div class="text-lg font-semibold">{{ session.user.value.displayName }}</div>
        <MutedText size="sm">@{{ session.user.value.username }}</MutedText>
      </div>
    </div>
    <div v-else>
      <MutedText size="sm">{{ t('settings.notSignedIn') }}</MutedText>
    </div>
  </div>

  <div class="mt-6">
    <DefaultPreferencesCard />
  </div>

  <div class="mt-6">
    <CharacterCard />
  </div>

  <div class="mt-6">
    <RetainerListCard />
  </div>

  <div class="mt-6">
    <SkillLevelsCard />
  </div>

  <div class="mt-6">
    <AlertWebhookCard />
  </div>

  <div class="mt-6">
    <SessionListCard />
  </div>

  <div class="mt-6">
    <SettingsDangerZoneCard />
  </div>
</template>
