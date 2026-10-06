/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSession } from '@/composables/useSession'
import SubHeader from '@/components/typography/SubHeader.vue'
import BasketButton from '@/components/ffxiv/BasketButton.vue'

const { t } = useI18n()

const session = useSession()
const router = useRouter()
const route = useRoute()

/** Primary destinations, shared by the desktop bar and the mobile menu. */
const NAV_LINKS = ['dashboard', 'offers', 'planner', 'baskets', 'alerts', 'desynth', 'shopping'] as const

const menuOpen = ref(false)

watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
  },
)

defineEmits<{ openBasket: [] }>()

async function handleLogout() {
  await session.logout()
  await router.push({ name: 'login' })
}
</script>

<template>
  <header class="border-b border-(--border) bg-(--bg-accent)" @keydown.escape="menuOpen = false">
    <div class="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3 sm:px-6">
      <RouterLink :to="{ name: 'dashboard' }" class="flex items-center gap-3">
        <FontAwesomeIcon :icon="['fas', 'coins']" class="text-(--color-primary) text-xl" />
        <SubHeader>Lolorito</SubHeader>
      </RouterLink>
      <nav v-if="session.isAuthenticated.value" class="ml-4 hidden min-w-0 flex-wrap items-center gap-x-3 gap-y-1 text-sm md:flex">
        <RouterLink
          v-for="name in NAV_LINKS"
          :key="name"
          :to="{ name }"
          class="text-(--text-muted) hover:text-(--text)"
          active-class="text-(--text) font-semibold"
        >
          {{ t(`nav.${name}`) }}
        </RouterLink>
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
            <span v-if="!session.user.value.avatarUrl" class="lg:hidden" :title="t('nav.settings')">
              <FontAwesomeIcon :icon="['fas', 'gear']" />
            </span>
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
          <button
            type="button"
            class="rounded-(--radius-theme) border border-(--border) px-3 py-1 text-sm hover:bg-(--bg) md:hidden"
            :aria-label="menuOpen ? t('nav.closeMenu') : t('nav.openMenu')"
            :aria-expanded="menuOpen"
            aria-controls="mobile-nav"
            @click="menuOpen = !menuOpen"
          >
            <FontAwesomeIcon :icon="['fas', menuOpen ? 'xmark' : 'bars']" />
          </button>
        </template>
      </div>
    </div>
    <nav
      v-if="menuOpen && session.isAuthenticated.value"
      id="mobile-nav"
      class="border-t border-(--border) px-4 py-2 md:hidden"
    >
      <RouterLink
        v-for="name in [...NAV_LINKS, 'settings']"
        :key="name"
        :to="{ name }"
        class="block rounded-(--radius-theme) px-2 py-2 text-(--text-muted) hover:bg-(--bg) hover:text-(--text)"
        active-class="text-(--text) font-semibold"
      >
        {{ t(`nav.${name}`) }}
      </RouterLink>
    </nav>
  </header>
</template>
