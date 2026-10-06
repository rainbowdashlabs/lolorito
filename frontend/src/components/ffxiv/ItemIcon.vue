/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { xivapiIconUrl } from '@/util/xivapi'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  itemId: number
  size?: number
  alt?: string
}>(), {
  size: 32,
  alt: '',
})

const url = computed(() => xivapiIconUrl(props.itemId))
const failed = ref(false)
const showPlaceholder = computed(() => failed.value)

function onError() {
  failed.value = true
}
</script>

<template>
  <span
    class="inline-block overflow-hidden rounded border border-(--border) bg-(--bg-accent) align-middle"
    :style="{ width: `${size}px`, height: `${size}px` }"
    :aria-label="alt || t('common.itemNumber', { id: itemId })"
  >
    <img
      v-if="!showPlaceholder"
      :src="url"
      :alt="alt || String(itemId)"
      :width="size"
      :height="size"
      loading="lazy"
      class="h-full w-full object-cover"
      @error="onError"
    />
    <FontAwesomeIcon
      v-else
      :icon="['fas', 'boxes']"
      class="text-(--text-muted) h-full w-full p-1"
    />
  </span>
</template>
