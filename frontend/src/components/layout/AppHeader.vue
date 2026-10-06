/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSession } from '@/composables/useSession'
import SubHeader from '@/components/typography/SubHeader.vue'
import BasketButton from '@/components/ffxiv/BasketButton.vue'

const { t } = useI18n()

const session = useSession()
const router = useRouter()

defineEmits<{ openBasket: [] }>()

async function handleLogout() {
  await session.logout()
  await router.push({ name: 'login' })
}
</script>

<template>
  <header class="border-b border-(--border) bg-(--bg-accent)">
    <div class="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3 sm:px-6">
      <RouterLink :to="{ name: 'dashboard' }" class="flex items-center gap-3">
        <FontAwesomeIcon :icon="['fas', 'coins']" class="text-(--color-primary) text-xl" />
        <SubHeader>Lolorito</SubHeader>
      </RouterLink>
      <nav v-if="session.isAuthenticated.value" class="ml-4 hidden min-w-0 flex-wrap items-center gap-x-3 gap-y-1 text-sm md:flex">
        <RouterLink :to="{ name: 'dashboard' }" class="text-(--text-muted) hover:text-(--text)" active-class="text-(--text) font-semibold">{{ t('nav.dashboard') }}</RouterLink>
        <RouterLink :to="{ name: 'offers' }" class="text-(--text-muted) hover:text-(--text)" active-class="text-(--text) font-semibold">{{ t('nav.offers') }}</RouterLink>
        <RouterLink :to="{ name: 'planner' }" class="text-(--text-muted) hover:text-(--text)" active-class="text-(--text) font-semibold">{{ t('nav.planner') }}</RouterLink>
        <RouterLink :to="{ name: 'baskets' }" class="text-(--text-muted) hover:text-(--text)" active-class="text-(--text) font-semibold">{{ t('nav.baskets') }}</RouterLink>
        <RouterLink :to="{ name: 'alerts' }" class="text-(--text-muted) hover:text-(--text)" active-class="text-(--text) font-semibold">{{ t('nav.alerts') }}</RouterLink>
        <RouterLink :to="{ name: 'desynth' }" class="text-(--text-muted) hover:text-(--text)" active-class="text-(--text) font-semibold">{{ t('nav.desynth') }}</RouterLink>
        <RouterLink :to="{ name: 'shopping' }" class="text-(--text-muted) hover:text-(--text)" active-class="text-(--text) font-semibold">{{ t('nav.shopping') }}</RouterLink>
      </nav>

      <div class="ml-auto flex shrink-0 items-center gap-3">
        <template v-if="session.isAuthenticated.value && session.user.value">
          <BasketButton @open="$emit('openBasket')" />
          <RouterLink :to="{ name: 'settings' }" class="flex items-center gap-2 rounded-full px-2 py-1 hover:bg-(--bg)">
            <img
              v-if="session.user.value.avatarUrl"
              :src="session.user.value.avatarUrl"
              :alt="session.user.value.displayName"
              class="h-7 w-7 rounded-full"
            />
            <span class="hidden text-sm font-medium lg:block">{{ session.user.value.displayName }}</span>
          </RouterLink>
          <button
            type="button"
            class="rounded-(--radius-theme) border border-(--border) px-3 py-1 text-sm hover:bg-(--bg)"
            :title="t('common.signOut')"
            @click="handleLogout"
          >
            <FontAwesomeIcon :icon="['fas', 'arrow-right-from-bracket']" />
          </button>
        </template>
      </div>
    </div>
  </header>
</template>
