/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { LOCALE_LABELS, SUPPORTED_LOCALES, setLocale, type Locale } from '@/i18n'
import { useTheme, type Theme } from '@/plugins/theme'
import { useSession } from '@/composables/useSession'
import { authApi } from '@/api'
import SelectInput from '@/components/input/select/SelectInput.vue'

const { t, locale } = useI18n()
const theme = useTheme()
const session = useSession()

const localeChoice = computed({
  get: () => locale.value as Locale,
  set: async (v: Locale) => {
    setLocale(v)
    if (session.isAuthenticated.value) {
      try {
        await authApi.setLocale(v)
      } catch {
        // local change already applied
      }
    }
  },
})

const themeChoice = computed({
  get: () => theme.preference.value,
  set: (v: Theme) => theme.setPreference(v),
})
</script>

<template>
  <footer class="mt-auto border-t border-(--border) bg-(--bg-accent) px-4 py-6 print:hidden">
    <div class="mx-auto flex max-w-6xl flex-col gap-6 md:grid md:grid-cols-3 md:gap-4">
      <div class="flex flex-col items-center gap-2 md:items-start">
        <div class="flex items-center gap-2 text-sm">
          <label class="text-(--text-muted)">{{ t('footer.theme') }}</label>
          <SelectInput v-model="themeChoice" class="max-w-[10rem]">
            <option value="dark">{{ t('settings.themeDark') }}</option>
            <option value="light">{{ t('settings.themeLight') }}</option>
            <option value="auto">{{ t('settings.themeAuto') }}</option>
          </SelectInput>
        </div>
        <div class="flex items-center gap-2 text-sm">
          <label class="text-(--text-muted)">{{ t('footer.language') }}</label>
          <SelectInput v-model="localeChoice" class="max-w-[10rem]">
            <option v-for="l in SUPPORTED_LOCALES" :key="l" :value="l">{{ LOCALE_LABELS[l] }}</option>
          </SelectInput>
        </div>
      </div>

      <div class="flex flex-col items-center gap-1 text-center text-sm text-(--text-muted)">
        <a
          href="https://github.com/rainbowdashlabs/lolorito"
          target="_blank"
          rel="noopener"
          class="text-(--link) hover:underline inline-flex items-center gap-1"
        >
          <FontAwesomeIcon :icon="['fab', 'github']" />
          {{ t('footer.github') }}
        </a>
        <span>{{ t('footer.copyright') }}</span>
        <span>{{ t('footer.madeWith') }}</span>
        <span>{{ t('footer.license') }}</span>
      </div>

      <div class="hidden md:block" />
    </div>
  </footer>
</template>
