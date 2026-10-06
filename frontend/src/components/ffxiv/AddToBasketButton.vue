/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import { useBasket } from '@/composables/useBasket'
import type { BasketAction, BasketItem } from '@/composables/useBasket'
import { pushToast } from '@/composables/useToasts'

const props = withDefaults(defineProps<{
  itemId: number
  itemName: string
  hq: boolean
  sourceWorldId: number
  sourceWorldName: string
  quantity: number
  buyPrice: number
  evPerHour: number
  action?: BasketAction
  compact?: boolean
}>(), {
  action: 'resale',
  compact: false,
})

const { t } = useI18n()
const basket = useBasket()

function add() {
  const payload: Omit<BasketItem, 'key' | 'addedAt'> = {
    itemId: props.itemId,
    itemName: props.itemName,
    hq: props.hq,
    sourceWorldId: props.sourceWorldId,
    sourceWorldName: props.sourceWorldName,
    quantity: props.quantity,
    buyPrice: props.buyPrice,
    evPerHour: props.evPerHour,
    action: props.action,
  }
  basket.add(payload)
  pushToast(
    t(props.hq ? 'toasts.basketAddedHq' : 'toasts.basketAdded', { name: props.itemName }),
    'success',
  )
}
</script>

<template>
  <SecondaryButton v-if="compact" :icon="['fas', 'basket-shopping']" @click.stop="add">
    {{ t('common.add') }}
  </SecondaryButton>
  <PrimaryButton v-else :icon="['fas', 'basket-shopping']" @click.stop="add">
    {{ t('basket.addToBasket') }}
  </PrimaryButton>
</template>
