/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, watchEffect } from 'vue'

export type Theme = 'dark' | 'light' | 'auto'

const STORAGE_KEY = 'lolorito_theme_v1'
const preference = ref<Theme>('dark')

function loadInitial(): Theme {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (raw === 'light' || raw === 'dark' || raw === 'auto') return raw
  } catch {
    // ignore
  }
  return 'auto'
}

function effectiveTheme(pref: Theme): 'dark' | 'light' {
  if (pref === 'dark' || pref === 'light') return pref
  if (typeof window === 'undefined' || !window.matchMedia) return 'dark'
  return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

/**
 * Applies the theme to the document, listens for the OS scheme change when
 * `auto` is selected, and returns a reactive handle the settings UI can
 * bind to.
 */
export function installTheme() {
  preference.value = loadInitial()

  watchEffect(() => {
    const applied = effectiveTheme(preference.value)
    document.documentElement.setAttribute('data-theme', applied)
    document.documentElement.classList.toggle('dark', applied === 'dark')
  })

  if (typeof window !== 'undefined' && window.matchMedia) {
    const mq = window.matchMedia('(prefers-color-scheme: light)')
    mq.addEventListener('change', () => {
      if (preference.value === 'auto') {
        // Trigger the watchEffect by writing the same value back.
        preference.value = 'auto'
        document.documentElement.setAttribute('data-theme', effectiveTheme('auto'))
      }
    })
  }
}

export function useTheme() {
  return {
    preference,
    setPreference(next: Theme) {
      preference.value = next
      try {
        window.localStorage.setItem(STORAGE_KEY, next)
      } catch {
        // ignore
      }
    },
  }
}
