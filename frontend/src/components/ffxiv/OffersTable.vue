/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import THead from '@/components/table/THead.vue'
import Th from '@/components/table/Th.vue'
import TRow from '@/components/table/TRow.vue'
import Td from '@/components/table/Td.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import WorldBadge from '@/components/ffxiv/WorldBadge.vue'
import AddToBasketButton from '@/components/ffxiv/AddToBasketButton.vue'
import AdversaryBadges from '@/components/ffxiv/AdversaryBadges.vue'
import ConfidenceChip from '@/components/ffxiv/ConfidenceChip.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import OffersRowDetail from '@/components/ffxiv/OffersRowDetail.vue'
import type { ScoredOffer } from '@/api/offers'
import { useWorlds } from '@/composables/useWorlds'
import { formatHours } from '@/util/format'

const { t } = useI18n()

defineProps<{
  offers: ScoredOffer[]
  homeWorldId: number | null
}>()

defineEmits<{ open: [row: ScoredOffer] }>()

const worlds = useWorlds()
const expanded = ref<Set<string>>(new Set())

function worldName(id: number): string {
  return worlds.worldById(id).world?.name ?? String(id)
}

function keyOf(row: ScoredOffer): string {
  return `${row.sourceWorldId}-${row.itemId}-${row.hq}`
}

function toggle(row: ScoredOffer) {
  const k = keyOf(row)
  const next = new Set(expanded.value)
  if (next.has(k)) next.delete(k)
  else next.add(k)
  expanded.value = next
}

</script>

<template>
  <div class="-mx-4 overflow-x-auto px-4 sm:mx-0 sm:px-0">
    <table class="w-full min-w-[640px] text-sm">
      <THead>
        <Th class="uppercase text-xs tracking-wider text-(--text-muted)">{{ t('offers.col.item') }}</Th>
        <Th class="uppercase text-xs tracking-wider text-(--text-muted)">{{ t('offers.col.from') }}</Th>
        <Th align="right" class="uppercase text-xs tracking-wider text-(--text-muted)">
          {{ t('offers.col.buy') }}
        </Th>
        <Th align="right" class="uppercase text-xs tracking-wider text-(--text-muted)">
          {{ t('offers.col.qty') }}
        </Th>
        <Th align="right" class="uppercase text-xs tracking-wider text-(--text-muted)">
          <span :title="t('offers.col.expectedNetHint')" class="border-b border-dotted border-(--text-muted)">
            {{ t('offers.col.expectedNet') }}
          </span>
        </Th>
        <Th align="right" class="uppercase text-xs tracking-wider text-(--text-muted)">
          <span :title="t('offers.col.totalProfitHint')" class="border-b border-dotted border-(--text-muted)">
            {{ t('offers.col.totalProfit') }}
          </span>
        </Th>
        <Th align="right" class="uppercase text-xs tracking-wider text-(--text-muted)">
          <span :title="t('offers.col.sigmaHint')" class="border-b border-dotted border-(--text-muted)">
            {{ t('offers.col.sigma') }}
          </span>
        </Th>
        <Th align="right" class="uppercase text-xs tracking-wider text-(--text-muted)">
          <span :title="t('offers.col.expectedShelfHint')" class="border-b border-dotted border-(--text-muted)">
            {{ t('offers.col.expectedShelf') }}
          </span>
        </Th>
        <Th align="right" class="uppercase text-xs tracking-wider text-(--text-muted)">
          <span :title="t('offers.col.gilPerHourHint')" class="border-b border-dotted border-(--text-muted)">
            {{ t('offers.col.gilPerHour') }}
          </span>
        </Th>
        <Th class="uppercase text-xs tracking-wider text-(--text-muted)">
          <span :title="t('confidence.hint')" class="border-b border-dotted border-(--text-muted)">
            {{ t('offers.col.confidence') }}
          </span>
        </Th>
        <Th class="uppercase text-xs tracking-wider text-(--text-muted)" :title="t('offersFilter.flagsColumnTitle')">
          {{ t('offersFilter.flagsColumn') }}
        </Th>
        <Th align="right" />
      </THead>
      <tbody>
        <template v-for="row in offers" :key="keyOf(row)">
        <TRow
          class="cursor-pointer hover:bg-(--bg-accent)"
          @click="$emit('open', row)"
        >
          <Td>
            <span class="inline-flex items-center gap-2">
              <button
                type="button"
                class="text-(--text-muted) hover:text-(--text) w-4 text-center"
                :aria-label="expanded.has(keyOf(row)) ? t('offers.collapseRow') : t('offers.expandRow')"
                @click.stop="toggle(row)"
              >
                {{ expanded.has(keyOf(row)) ? '▾' : '▸' }}
              </button>
              <ItemIcon :item-id="row.itemId" :size="24" :alt="row.itemName" />
              <span class="font-medium">{{ row.itemName }}</span>
              <HQMark :hq="row.hq" />
            </span>
          </Td>
          <Td>
            <WorldBadge :world-name="worldName(row.sourceWorldId)" />
          </Td>
          <Td align="right"><GilAmount :value="row.buyPrice" /></Td>
          <Td align="right">
            <template v-if="row.originalQuantity != null && row.originalQuantity !== row.quantity">
              <span :title="t('offers.partialQtyHint', { qty: row.quantity, orig: row.originalQuantity })">
                {{ row.quantity }}
                <span class="text-(--text-muted)">/ {{ row.originalQuantity }}</span>
              </span>
            </template>
            <template v-else>{{ row.quantity }}</template>
          </Td>
          <Td align="right">
            <span :title="t('offers.listAtHint', { ratio: row.valuation.listRatio.toFixed(2) })">
              <GilAmount :value="row.valuation.expectedNet" />
              <span class="ml-1 text-xs text-(--text-muted)">@{{ row.valuation.listRatio.toFixed(2) }}×</span>
            </span>
          </Td>
          <Td align="right">
            <span
              class="font-medium"
              :class="row.valuation.evGross >= 0 ? 'text-(--color-success)' : 'text-(--color-error)'"
              :title="t('offers.profitFormulaHint', {
                qty: row.quantity,
                net: Math.round(row.valuation.expectedNet).toLocaleString(),
                buy: row.buyPrice.toLocaleString(),
              })"
            >
              <GilAmount :value="row.valuation.evGross" />
            </span>
          </Td>
          <Td align="right" class="text-(--text-muted)">
            <span :title="t('offers.sigmaTitle', {
              sigma: Math.round(row.valuation.sigmaNet).toLocaleString(),
              ratio: (row.valuation.expectedNet !== 0 ? row.valuation.sigmaNet / Math.abs(row.valuation.expectedNet) : 0).toFixed(2),
            })">
              ±<GilAmount :value="row.valuation.sigmaNet" />
            </span>
          </Td>
          <Td align="right">
            <span
              :title="row.depthAhead > 0
                ? t('offers.depthAheadHint', { depth: row.depthAhead })
                : t('offers.depthEmptyHint')"
            >
              {{ formatHours(row.valuation.expectedTimeOnShelfHours) }}
              <span v-if="row.depthAhead > 0" class="ml-1 text-xs text-(--text-muted)">+{{ row.depthAhead }}q</span>
            </span>
          </Td>
          <Td align="right">
            <span class="font-semibold text-(--color-success)"><GilAmount :value="row.valuation.evPerHour" /></span>
          </Td>
          <Td><ConfidenceChip :confidence="row.confidence" /></Td>
          <Td>
            <AdversaryBadges
              :lambda-undercut="row.lambdaUndercut"
              :ghost-fraction="row.ghostFraction"
              :model-sufficient="row.modelSufficient"
              :model-pooled="row.modelPooled"
            />
          </Td>
          <Td align="right">
            <AddToBasketButton
              compact
              :item-id="row.itemId"
              :item-name="row.itemName"
              :hq="row.hq"
              :source-world-id="row.sourceWorldId"
              :source-world-name="worldName(row.sourceWorldId)"
              :quantity="row.quantity"
              :buy-price="row.buyPrice"
              :ev-per-hour="row.valuation.evPerHour"
              action="resale"
            />
          </Td>
        </TRow>
        <tr v-if="expanded.has(keyOf(row))" class="border-b border-(--border) bg-(--bg-accent)">
          <td :colspan="12">
            <OffersRowDetail
              :item-id="row.itemId"
              :hq="row.hq"
              :source-world-id="row.sourceWorldId"
              :home-world-id="homeWorldId ?? 0"
            />
          </td>
        </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>
