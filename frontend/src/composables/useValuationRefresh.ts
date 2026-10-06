/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { onBeforeUnmount, onMounted } from 'vue'
import type { ValuationEvent } from '@/composables/useValuationStream'

/**
 * Listen for {@code lolorito:model-refit} events broadcast by the
 * WebSocket cache invalidator and fire {@code onRefit} for those that
 * pass {@code predicate}. Multiple events inside {@code debounceMs} are
 * coalesced into a single call — a busy refit sweep can produce dozens
 * of frames in a second and we only want one refetch per burst.
 *
 * <p>Consumers wire this next to their existing {@code onMounted}
 * refresh so the WS frame path and the polling path stay in agreement.
 * The custom-event bridge means a view doesn't have to know that the
 * source is a WebSocket.
 */
export function useValuationRefresh(
    predicate: (e: ValuationEvent) => boolean,
    onRefit: () => void,
    debounceMs = 750,
): void {
  let pending: ReturnType<typeof setTimeout> | null = null

  function schedule() {
    if (pending) clearTimeout(pending)
    pending = setTimeout(() => {
      pending = null
      onRefit()
    }, debounceMs)
  }

  function handler(evt: Event) {
    const custom = evt as CustomEvent<ValuationEvent>
    if (!custom.detail) return
    if (!predicate(custom.detail)) return
    schedule()
  }

  onMounted(() => window.addEventListener('lolorito:model-refit', handler))
  onBeforeUnmount(() => {
    window.removeEventListener('lolorito:model-refit', handler)
    if (pending) clearTimeout(pending)
  })
}
