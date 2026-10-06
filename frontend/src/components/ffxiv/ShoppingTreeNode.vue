/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import ToggleSwitch from '@/components/input/toggle/ToggleSwitch.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import type { ShoppingDecision, ShoppingNode } from '@/api/shopping'

const { t } = useI18n()

const props = defineProps<{
  node: ShoppingNode
  /** Items whose buys are priced on the HQ board — owned by the view. */
  hqItemIds: ReadonlySet<number>
  /** True inside a bought subtree — the children are only a craft preview. */
  dimmed?: boolean
}>()

const emit = defineEmits<{
  toggleDecision: [itemId: number, decision: ShoppingDecision]
  toggleHq: [itemId: number, hq: boolean]
}>()

/** Writable view over `decision` — flipping asks the parent to re-plan with an override. */
const decision = computed<string>({
  get: () => props.node.decision,
  set: (v) => emit('toggleDecision', props.node.itemId, v === 'craft' ? 'CRAFT' : 'BUY'),
})

const hqChecked = computed<boolean>({
  get: () => props.hqItemIds.has(props.node.itemId),
  set: (v) => emit('toggleHq', props.node.itemId, v),
})

/** Children of a buy-decision node are a preview of what crafting would need. */
const childrenDimmed = computed(() => props.dimmed || props.node.decision === 'buy')
</script>

<template>
  <div :class="dimmed ? 'opacity-50' : ''">
    <div class="flex flex-wrap items-center gap-2 rounded-(--radius-theme) bg-(--bg) px-3 py-2 text-sm">
      <ItemIcon :item-id="node.itemId" :size="20" :alt="node.itemName" />
      <span class="font-medium">{{ node.itemName }}</span>
      <HQMark :hq="node.hq" />
      <MutedText size="sm">× {{ node.qty }}</MutedText>
      <FieldLabel
        v-if="node.canBeHq"
        hint
        inline
        :title="t('shopping.hqTitle')"
      >
        <CheckboxInput v-model="hqChecked" />
        {{ t('shopping.hq') }}
      </FieldLabel>
      <span
        v-if="node.craftClass"
        class="rounded bg-(--bg-accent) px-1.5 py-0.5 text-xs capitalize text-(--text-muted)"
      >
        {{ node.craftClass }} L{{ node.craftLevel }}
      </span>
      <div class="ml-auto flex flex-wrap items-center gap-3">
        <MutedText size="sm">
          {{ t('shopping.buyUnit') }}
          <GilAmount v-if="node.buyUnitCost != null" :value="node.buyUnitCost" />
          <span v-else>—</span>
          · {{ t('shopping.craftUnit') }}
          <GilAmount v-if="node.craftUnitCost != null" :value="node.craftUnitCost" />
          <span v-else>—</span>
        </MutedText>
        <ToggleSwitch
          v-if="node.craftable"
          v-model="decision"
          option-a="buy"
          option-b="craft"
          :label-a="t('shopping.buy')"
          :label-b="t('shopping.craft')"
        />
      </div>
    </div>
    <div
      v-if="node.children.length > 0"
      class="mt-1 ml-5 grid gap-1 border-l border-(--border) pl-3"
      :title="childrenDimmed && !dimmed ? t('shopping.buyPreviewHint') : undefined"
    >
      <ShoppingTreeNode
        v-for="child in node.children"
        :key="child.itemId"
        :node="child"
        :hq-item-ids="hqItemIds"
        :dimmed="childrenDimmed"
        @toggle-decision="(id, d) => emit('toggleDecision', id, d)"
        @toggle-hq="(id, v) => emit('toggleHq', id, v)"
      />
    </div>
  </div>
</template>
