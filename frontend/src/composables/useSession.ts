/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, computed } from 'vue'
import { authApi } from '@/api'
import type { MeResponse } from '@/api/auth'
import { applyServerLocale } from '@/i18n'

/**
 * Module-scoped singleton — mirrors Ember's pattern (no Pinia). Any component
 * or route guard that calls {@link useSession} gets the same reactive user
 * state.
 */
const currentUser = ref<MeResponse | null>(null)
const status = ref<'unknown' | 'loading' | 'authenticated' | 'anonymous'>('unknown')

async function load(): Promise<void> {
  status.value = 'loading'
  const user = await authApi.me()
  currentUser.value = user
  status.value = user ? 'authenticated' : 'anonymous'
  if (user) applyServerLocale(user.locale)
}

async function logout(): Promise<void> {
  await authApi.logout()
  currentUser.value = null
  status.value = 'anonymous'
}

function loginRedirect(): void {
  window.location.href = authApi.loginRedirectUrl()
}

export function useSession() {
  return {
    user: currentUser,
    status,
    isAuthenticated: computed(() => status.value === 'authenticated'),
    load,
    logout,
    loginRedirect,
  }
}
