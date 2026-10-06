/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'
import { itemSearchApi } from '@/api'
import type { ItemSearchHit } from '@/api/itemSearch'
import { itemLevelFor, preloadItemCatalog } from '@/util/xivapi'

const props = defineProps<{ modelValue: ItemSearchHit | null; placeholder?: string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [ItemSearchHit | null] }>()

const { t } = useI18n()

const query = ref('')
const results = ref<ItemSearchHit[]>([])
const loading = ref(false)
const showList = ref(false)
let debounceTimer: ReturnType<typeof setTimeout> | null = null

watch(query, (q) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (!q.trim()) {
    results.value = []
    return
  }
  loading.value = true
  debounceTimer = setTimeout(async () => {
    try {
      results.value = await itemSearchApi.search(q, 15)
    } catch {
      results.value = []
    } finally {
      loading.value = false
    }
  }, 180)
})

function pick(hit: ItemSearchHit) {
  emit('update:modelValue', hit)
  query.value = hit.name
  showList.value = false
}

function clear() {
  emit('update:modelValue', null)
  query.value = ''
  results.value = []
  showList.value = false
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) query.value = v.name
    if (!v) query.value = ''
  },
  { immediate: true },
)

onMounted(() => {
  preloadItemCatalog()
})
</script>

<template>
  <div class="relative">
    <div class="flex items-center gap-2 rounded-(--radius-theme) border border-(--input-border) bg-(--input-bg) px-3 py-2">
      <FontAwesomeIcon :icon="['fas', 'magnifying-glass']" class="text-(--text-muted)" />
      <input
        v-model="query"
        type="text"
        :placeholder="placeholder ?? t('itemPicker.placeholder')"
        :disabled="disabled"
        class="flex-1 bg-transparent outline-none"
        autocomplete="off"
        @focus="showList = true"
      />
      <button
        v-if="modelValue"
        type="button"
        class="text-(--text-muted) hover:text-(--text)"
        :title="t('itemPicker.clear')"
        @click="clear"
      >
        <FontAwesomeIcon :icon="['fas', 'xmark']" />
      </button>
    </div>

    <ul
      v-if="showList && query.trim().length > 0"
      class="absolute z-30 mt-1 max-h-80 w-full overflow-y-auto rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) shadow-lg"
    >
      <li v-if="loading" class="px-3 py-2 text-sm">
        <MutedText size="sm">{{ t('common.loading') }}</MutedText>
      </li>
      <li v-else-if="results.length === 0" class="px-3 py-2 text-sm">
        <MutedText size="sm">{{ t('itemPicker.noResults', { q: query }) }}</MutedText>
      </li>
      <li
        v-for="hit in results"
        :key="hit.itemId"
        class="cursor-pointer border-t border-(--border) first:border-t-0 hover:bg-(--bg)"
        @mousedown.prevent="pick(hit)"
      >
        <div class="flex items-center gap-3 px-3 py-2">
          <ItemIcon :item-id="hit.itemId" :size="28" :alt="hit.name" />
          <div class="min-w-0 flex-1">
            <div class="truncate font-medium">{{ hit.name }}</div>
            <MutedText size="sm">
              #{{ hit.itemId }}
              <span v-if="itemLevelFor(hit.itemId)"> · {{ t('itemPicker.ilvl', { v: itemLevelFor(hit.itemId) }) }}</span>
            </MutedText>
          </div>
        </div>
      </li>
    </ul>
  </div>
</template>
