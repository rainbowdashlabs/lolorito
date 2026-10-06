/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref } from 'vue'

export type ToastVariant = 'info' | 'success' | 'warning' | 'error'

export interface Toast {
  id: number
  message: string
  variant: ToastVariant
  timeoutMs: number
}

const toasts = ref<Toast[]>([])
let nextId = 1

/**
 * Push a toast. Auto-dismisses after {@link Toast.timeoutMs}. Consumers
 * that don't want the automatic dismissal pass {@code timeoutMs = 0}
 * and call {@link dismiss} themselves.
 */
export function pushToast(message: string, variant: ToastVariant = 'info', timeoutMs = 4_000): number {
  const id = nextId++
  toasts.value = [...toasts.value, { id, message, variant, timeoutMs }]
  if (timeoutMs > 0) {
    window.setTimeout(() => dismiss(id), timeoutMs)
  }
  return id
}

export function dismiss(id: number) {
  toasts.value = toasts.value.filter((t) => t.id !== id)
}

export function useToasts() {
  return { toasts, pushToast, dismiss }
}
