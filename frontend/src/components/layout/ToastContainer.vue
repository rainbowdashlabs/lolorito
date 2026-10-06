/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import { useToasts } from '@/composables/useToasts'
import type { ToastVariant } from '@/composables/useToasts'

const { t } = useI18n()
const { toasts, dismiss } = useToasts()

const variantClass: Record<ToastVariant, string> = {
  info: 'border-(--color-info) bg-(--bg)',
  success: 'border-(--color-success) bg-(--bg)',
  warning: 'border-(--color-warning) bg-(--bg)',
  error: 'border-(--color-error) bg-(--bg)',
}
</script>

<template>
  <Teleport to="body">
    <div class="pointer-events-none fixed bottom-4 right-4 z-50 flex w-80 flex-col gap-2">
      <TransitionGroup name="toast" tag="div" class="flex flex-col gap-2">
        <div
          v-for="toast in toasts"
          :key="toast.id"
          :class="[
            'pointer-events-auto flex items-start justify-between gap-3 rounded-(--radius-theme) border-2 px-3 py-2 text-sm shadow-(--shadow)',
            variantClass[toast.variant],
          ]"
          role="status"
        >
          <div class="flex-1 whitespace-pre-line">{{ toast.message }}</div>
          <button
            type="button"
            class="opacity-70 hover:opacity-100"
            :aria-label="t('common.dismiss')"
            @click="dismiss(toast.id)"
          >
            ×
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 180ms ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>
