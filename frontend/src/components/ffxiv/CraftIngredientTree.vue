/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type { CraftIngredientDto } from '@/api/items'

const props = defineProps<{
  ingredient: CraftIngredientDto
  depth?: number
  /**
   * User's crafter levels keyed by class name. When present, sub-recipes
   * the user can't do (level too low, or class missing) are annotated
   * "requires X lv Y" and their [expand] control is dimmed. Null →
   * don't filter, render exactly what the backend returned.
   */
  craftLevels?: Record<string, number>
  /**
   * Global override from `ItemActionsPanel`'s "Expand all / Collapse all"
   * toggle. When it flips, every subtree syncs its local `expanded` ref
   * to match — individual per-node clicks still work afterwards, so
   * "expand everything, then collapse the one branch I don't care about"
   * is the intended flow. `null`/`undefined` means "no override".
   */
  expandAll?: boolean | null
}>()

const { t } = useI18n()
const expanded = ref(false)

watch(
  () => props.expandAll,
  (v) => {
    if (v != null) expanded.value = v
  },
  { immediate: true },
)

/** True when the user can't touch the sub-recipe. Only meaningful when {@link props.ingredient} carries a `subRecipeClass`. */
const gated = computed(() => {
  if (!props.craftLevels) return false
  const cls = props.ingredient.subRecipeClass
  if (!cls) return false
  const need = props.ingredient.subRecipeLevel ?? 0
  const have = props.craftLevels[cls] ?? 0
  return have < need
})
</script>

<template>
  <li>
    <div class="flex flex-wrap items-center gap-1">
      <router-link
        :to="{ name: 'item', params: { id: String(ingredient.itemId) } }"
        class="inline-flex items-center gap-1 hover:text-(--color-primary-accent) hover:underline"
        :title="t('item.openDetail', { name: ingredient.itemName })"
      >
        <ItemIcon :item-id="ingredient.itemId" :size="20" :alt="ingredient.itemName" class="shrink-0" />
        <span>{{ ingredient.itemName }}</span>
      </router-link>
      <span>× {{ ingredient.quantity }}</span>
      <template v-if="ingredient.chosenSource === 'craft' && ingredient.craftPerUnit != null">
        <span class="text-(--color-success)">· {{ t('item.craftLabel') }} <GilAmount :value="ingredient.craftPerUnit" /></span>
        <MutedText v-if="ingredient.cheapestBuy != null" size="sm">
          ({{ t('item.buyLabel') }} <GilAmount :value="ingredient.cheapestBuy" />)
        </MutedText>
      </template>
      <template v-else-if="ingredient.chosenSource === 'buy' && ingredient.cheapestBuy != null">
        <span>· {{ t('item.buyLabel') }} <GilAmount :value="ingredient.cheapestBuy" /></span>
        <MutedText v-if="ingredient.craftPerUnit != null" size="sm">
          ({{ t('item.craftLabel') }} <GilAmount :value="ingredient.craftPerUnit" />)
        </MutedText>
      </template>
      <MutedText v-else size="sm">· {{ t('item.noListingsShort') }}</MutedText>
      <MutedText v-if="ingredient.depthCapped" size="sm" :title="t('item.depthCappedHint')">
        · {{ t('item.depthCapped') }}
      </MutedText>
      <MutedText v-if="gated" size="sm" class="text-(--color-warning)">
        · {{ t('item.needsClassLv', { class: ingredient.subRecipeClass, level: ingredient.subRecipeLevel }) }}
      </MutedText>
      <button
        v-if="ingredient.subIngredients && ingredient.subIngredients.length > 0"
        type="button"
        class="ml-1 hover:text-(--text)"
        :class="gated ? 'text-(--text-muted) opacity-60' : 'text-(--text-muted)'"
        @click="expanded = !expanded"
      >
        <span v-if="expanded">{{ t('item.hide') }}</span>
        <span v-else>{{ t('item.expand') }}</span>
      </button>
    </div>
    <ul
      v-if="expanded && ingredient.subIngredients && ingredient.subIngredients.length > 0"
      class="mt-1 ml-4 border-l border-(--border) pl-3 space-y-1"
    >
      <CraftIngredientTree
        v-for="sub in ingredient.subIngredients"
        :key="sub.itemId"
        :ingredient="sub"
        :depth="(depth ?? 0) + 1"
        :craft-levels="craftLevels"
        :expand-all="expandAll"
      />
    </ul>
  </li>
</template>
