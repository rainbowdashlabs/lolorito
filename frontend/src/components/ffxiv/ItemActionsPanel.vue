/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import AddToBasketButton from '@/components/ffxiv/AddToBasketButton.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import CraftIngredientTree from '@/components/ffxiv/CraftIngredientTree.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ItemDetailDto, ItemListingDto } from '@/api/items'
import { list as loadSkills } from '@/api/skills'

const { t } = useI18n()

const props = defineProps<{
  item: ItemDetailDto
}>()

function cheapestSource(): ItemListingDto | null {
  const rows = props.item.listings.filter((l) => l.worldId !== props.item.homeWorldId)
  if (rows.length === 0) return null
  return rows.slice().sort((a, b) => a.unitPrice - b.unitPrice)[0]
}

const source = cheapestSource()

// Recipe picker — multiple recipes can produce the same item (base + rare
// intermediates for the same job). Default to the best EV/hr; user can
// swap for any other. When there's only one recipe we skip the picker.
const selectedRecipeId = ref<number | null>(pickBestRecipe())
const activeRecipe = computed(
  () => props.item.crafts.find((c) => c.recipeId === selectedRecipeId.value) ?? props.item.crafts[0] ?? null,
)

function pickBestRecipe(): number | null {
  const list = props.item.crafts
  if (list.length === 0) return null
  const scored = list.slice().sort((a, b) => (b.valuation?.evPerHour ?? -Infinity) - (a.valuation?.evPerHour ?? -Infinity))
  return scored[0].recipeId
}

// Fetch the user's crafter levels once so the ingredient tree can dim
// sub-recipes they can't touch. Best-effort — a load failure just means
// the tree renders without the class filter.
const craftLevels = ref<Record<string, number> | undefined>(undefined)
onMounted(async () => {
  try {
    const skills = await loadSkills()
    craftLevels.value = skills.craft
  } catch {
    craftLevels.value = undefined
  }
})

// Global "Expand all" flip for the craft tree. Flipping this drives
// every {@link CraftIngredientTree} node's local `expanded` via a
// prop-watch — individual [hide] clicks still work afterwards, so a
// user can expand everything and then collapse the one branch they
// don't care about.
const expandAll = ref<boolean | null>(null)
function toggleExpandAll() {
  expandAll.value = expandAll.value === true ? false : true
}
</script>

<template>
  <div>
    <SectionHeader>{{ t('item.actions') }}</SectionHeader>
    <div class="mt-3 grid gap-4 md:grid-cols-3">
      <PrimaryContainer>
        <div class="flex items-start gap-3">
          <FontAwesomeIcon :icon="['fas', 'basket-shopping']" class="mt-1 text-primary-accent" />
          <div class="flex-1">
            <div class="font-semibold">{{ t('item.resale') }}</div>
            <MutedText size="sm">{{ t('item.resaleHint', { world: item.homeWorldName }) }}</MutedText>
          </div>
        </div>
        <div v-if="source" class="mt-3 text-sm">
          {{ source.worldName }} · <GilAmount :value="source.unitPrice" /> × {{ source.quantity }}
        </div>
        <div class="mt-3">
          <AddToBasketButton
            v-if="source"
            :item-id="item.itemId"
            :item-name="item.itemName"
            :hq="item.hq"
            :source-world-id="source.worldId"
            :source-world-name="source.worldName"
            :quantity="source.quantity"
            :buy-price="source.unitPrice"
            :ev-per-hour="0"
            action="resale"
          />
          <MutedText v-else size="sm">{{ t('item.noSourceListings') }}</MutedText>
        </div>
      </PrimaryContainer>

      <SuccessContainer v-if="item.desynth">
        <div class="flex items-start gap-3">
          <FontAwesomeIcon :icon="['fas', 'hammer']" class="mt-1 text-success" />
          <div class="flex-1">
            <div class="font-semibold">{{ t('item.desynth') }}</div>
            <MutedText size="sm">{{ t('item.componentCount', { count: item.desynth.components.length }, item.desynth.components.length) }}</MutedText>
            <MutedText v-if="item.desynth.desynthClass" size="xs" class="mt-0.5 block capitalize">
              {{ t('item.requiresClass', { class: item.desynth.desynthClass }) }}<span v-if="item.desynth.desynthLevel"> L{{ item.desynth.desynthLevel }}</span>
            </MutedText>
          </div>
        </div>
        <div v-if="item.desynth.valuation" class="mt-3 text-sm">
          {{ t('item.expectedNetLabel') }} <GilAmount :value="item.desynth.valuation.expectedNet" /> {{ t('item.perSource') }}
        </div>
        <ul class="mt-2 space-y-1 text-xs">
          <li v-for="c in item.desynth.components" :key="c.itemId" class="flex items-center gap-1">
            <router-link
              :to="{ name: 'item', params: { id: String(c.itemId) } }"
              class="inline-flex items-center gap-1 hover:text-(--color-primary-accent) hover:underline"
              :title="t('item.openDetail', { name: c.itemName })"
            >
              <ItemIcon :item-id="c.itemId" :size="20" :alt="c.itemName" class="shrink-0" />
              <span>{{ c.itemName }}</span>
            </router-link>
            <span>× {{ c.avgQty.toFixed(2) }}</span>
            <span v-if="c.modelSufficient" class="ml-auto text-right">
              ≈ <GilAmount :value="Math.round(c.expectedNet / Math.max(c.avgQty, 0.001))" /> {{ t('item.each') }}
              · <GilAmount :value="Math.round(c.expectedNet)" /> {{ t('item.net') }}
            </span>
            <span v-else class="text-(--text-muted)">· {{ t('item.noModel') }}</span>
          </li>
        </ul>
      </SuccessContainer>
      <NeutralContainer v-else class="opacity-70">
        <div class="flex items-start gap-3">
          <FontAwesomeIcon :icon="['fas', 'hammer']" class="mt-1 text-(--text-muted)" />
          <div class="flex-1">
            <div class="font-semibold">{{ t('item.desynth') }}</div>
            <MutedText size="sm">{{ t('item.noDesynthData') }}</MutedText>
          </div>
        </div>
      </NeutralContainer>

      <SuccessContainer v-if="activeRecipe">
        <div class="flex items-start gap-3">
          <FontAwesomeIcon :icon="['fas', 'wrench']" class="mt-1 text-success" />
          <div class="flex-1">
            <div class="font-semibold">{{ t('item.craft') }}</div>
            <MutedText size="sm">
              {{ t('item.recipeMeta', { class: activeRecipe.craftClass, level: activeRecipe.level, yield: activeRecipe.yield }) }}
            </MutedText>
          </div>
        </div>
        <div v-if="item.crafts.length > 1" class="mt-3 flex items-center gap-2">
          <MutedText size="sm">{{ t('item.recipe') }}</MutedText>
          <SelectInput
            :model-value="String(selectedRecipeId ?? '')"
            class="flex-1"
            @update:model-value="selectedRecipeId = Number($event) || null"
          >
            <option
              v-for="c in item.crafts"
              :key="c.recipeId"
              :value="String(c.recipeId)"
            >
              {{ t('item.recipeOption', {
                class: c.craftClass,
                level: c.level,
                yield: c.yield,
                ev: c.valuation ? Math.round(c.valuation.evPerHour).toLocaleString() : '—',
              }) }}
            </option>
          </SelectInput>
        </div>
        <div v-if="activeRecipe.valuation" class="mt-3 text-sm">
          {{ t('item.evPerHourLabel') }} <GilAmount :value="activeRecipe.valuation.evPerHour" />
          <span
            class="ml-2 text-xs text-(--text-muted)"
            :title="t('item.listAtHint')"
          >
            {{ t('item.listAtRatio', { ratio: activeRecipe.valuation.listRatio.toFixed(2) }) }}
          </span>
        </div>
        <div class="mt-3 flex items-center justify-between text-xs">
          <MutedText size="xs">{{ t('item.ingredientCount', { count: activeRecipe.ingredients.length }, activeRecipe.ingredients.length) }}</MutedText>
          <button
            type="button"
            class="text-(--text-muted) underline hover:text-(--text)"
            @click="toggleExpandAll"
          >
            <span v-if="expandAll === true">{{ t('item.collapseAll') }}</span>
            <span v-else>{{ t('item.expandAll') }}</span>
          </button>
        </div>
        <ul class="mt-2 space-y-1 text-xs">
          <CraftIngredientTree
            v-for="ing in activeRecipe.ingredients"
            :key="ing.itemId"
            :ingredient="ing"
            :craft-levels="craftLevels"
            :expand-all="expandAll"
          />
        </ul>
      </SuccessContainer>
      <NeutralContainer v-else class="opacity-70">
        <div class="flex items-start gap-3">
          <FontAwesomeIcon :icon="['fas', 'wrench']" class="mt-1 text-(--text-muted)" />
          <div class="flex-1">
            <div class="font-semibold">{{ t('item.craft') }}</div>
            <MutedText size="sm">{{ t('item.noRecipe') }}</MutedText>
          </div>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
