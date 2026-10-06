/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import { useSession } from '@/composables/useSession'
import Heading from '@/components/typography/Heading.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

const { t } = useI18n()
const session = useSession()

function login() {
  session.loginRedirect()
}
</script>

<template>
  <div class="flex min-h-[70vh] items-center justify-center px-4">
    <NeutralContainer class="w-full max-w-md text-center">
      <FontAwesomeIcon :icon="['fas', 'coins']" class="text-(--color-primary) mb-4 text-4xl" />
      <Heading>Lolorito</Heading>
      <p class="mt-2 mb-6 text-(--text-muted)">
        {{ t('auth.loginBlurb') }}
      </p>
      <PrimaryButton full-width :icon="['fab', 'discord']" @click="login">
        {{ t('auth.continueWithDiscord') }}
      </PrimaryButton>
    </NeutralContainer>
  </div>
</template>
