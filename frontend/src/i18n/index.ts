/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { createI18n } from 'vue-i18n'
import en from './messages/en'
import de from './messages/de'
import fr from './messages/fr'
import ja from './messages/ja'

export const SUPPORTED_LOCALES = ['en', 'de', 'fr', 'ja'] as const
export type Locale = (typeof SUPPORTED_LOCALES)[number]

export const LOCALE_LABELS: Record<Locale, string> = {
  en: 'English',
  de: 'Deutsch',
  fr: 'Français',
  ja: '日本語',
}

const STORAGE_KEY = 'lolorito_locale_v1'

/** Pick a starting locale: saved → browser default → 'en'. */
function initialLocale(): Locale {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY)
    if (saved && (SUPPORTED_LOCALES as readonly string[]).includes(saved)) return saved as Locale
  } catch {
    // ignore
  }
  const nav = typeof navigator !== 'undefined' ? navigator.language.slice(0, 2) : 'en'
  return (SUPPORTED_LOCALES as readonly string[]).includes(nav) ? (nav as Locale) : 'en'
}

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale(),
  fallbackLocale: 'en',
  messages: { en, de, fr, ja },
})

export function setLocale(locale: Locale) {
  i18n.global.locale.value = locale
  try {
    window.localStorage.setItem(STORAGE_KEY, locale)
  } catch {
    // ignore
  }
  document.documentElement.setAttribute('lang', locale)
}

/** Sync SPA locale with a server-provided value (e.g. after /api/v1/me). */
export function applyServerLocale(locale: string | null | undefined) {
  if (!locale) return
  const short = locale.slice(0, 2)
  if ((SUPPORTED_LOCALES as readonly string[]).includes(short)) {
    setLocale(short as Locale)
  }
}

/** Map SPA locale to the Universalis Language names used by the backend. */
export function toBackendLanguage(locale: Locale): 'ENGLISH' | 'GERMAN' | 'FRENCH' | 'JAPANESE' {
  switch (locale) {
    case 'de':
      return 'GERMAN'
    case 'fr':
      return 'FRENCH'
    case 'ja':
      return 'JAPANESE'
    default:
      return 'ENGLISH'
  }
}
