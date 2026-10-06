/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import IconButton from '@/components/button/IconButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import HQMark from '@/components/ffxiv/HQMark.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import WorldBadge from '@/components/ffxiv/WorldBadge.vue'
import ItemIcon from '@/components/ffxiv/ItemIcon.vue'
import { useBasket, type BasketItem } from '@/composables/useBasket'
import { useSession } from '@/composables/useSession'
import { basketsApi } from '@/api'
import type { BasketVisibility } from '@/api/baskets'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const model = defineModel<boolean>({ default: false })
const basket = useBasket()
const session = useSession()
const router = useRouter()

const savePanelOpen = ref(false)
const saveName = ref('')
const saveVisibility = ref<BasketVisibility>('private')
const saving = ref(false)
const savedShareUrl = ref<string | null>(null)
const saveError = ref<string | null>(null)

function close() { model.value = false }

function openPlanner() {
  close()
  router.push({ name: 'planner' })
}

function openBasketPage() {
  close()
  router.push({ name: 'basket' })
}

function toggleSavePanel() {
  savePanelOpen.value = !savePanelOpen.value
  savedShareUrl.value = null
  saveError.value = null
  if (savePanelOpen.value && !saveName.value) {
    saveName.value = t('basket.defaultName', { date: new Date().toISOString().slice(0, 10) })
  }
}

function toWireItem(item: BasketItem) {
  return {
    key: item.key,
    itemId: item.itemId,
    itemName: item.itemName,
    hq: item.hq,
    sourceWorldId: item.sourceWorldId,
    sourceWorldName: item.sourceWorldName,
    quantity: item.quantity,
    buyPrice: item.buyPrice,
    action: item.action,
    evPerHour: item.evPerHour,
    addedAt: item.addedAt,
  }
}

async function save() {
  if (saving.value) return
  saving.value = true
  saveError.value = null
  try {
    const saved = await basketsApi.create({
      name: saveName.value.trim() || t('basket.untitled'),
      visibility: saveVisibility.value,
      items: basket.items.value.map(toWireItem),
    })
    savedShareUrl.value =
      saved.visibility === 'private' ? null : `${window.location.origin}/baskets/shared/${saved.shareToken}`
  } catch (e: unknown) {
    const obj = e as { response?: { data?: { error?: string } }; message?: string }
    saveError.value = obj?.response?.data?.error ?? obj?.message ?? String(e)
  } finally {
    saving.value = false
  }
}

async function copyShare() {
  if (!savedShareUrl.value) return
  try {
    await navigator.clipboard.writeText(savedShareUrl.value)
  } catch {
    // Silent — user can select the code block manually.
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="model" class="fixed inset-0 z-50">
        <div class="absolute inset-0 bg-black/50" @click="close" />
        <aside
          class="absolute right-0 top-0 flex h-full w-full max-w-md flex-col
                 border-l border-(--border) bg-(--bg) shadow-2xl"
        >
          <header class="flex items-center gap-3 border-b border-(--border) px-4 py-3">
            <SectionHeader>{{ t('basket.drawerTitle') }}</SectionHeader>
            <MutedText size="sm">{{ t('baskets.itemCount', { count: basket.count.value }, basket.count.value) }}</MutedText>
            <div class="ml-auto">
              <IconButton :icon="['fas', 'xmark']" :label="t('common.close')" @click="close" />
            </div>
          </header>

          <div class="flex-1 overflow-y-auto p-4">
            <EmptyState v-if="basket.count.value === 0" compact>
              {{ t('basket.drawerEmpty') }}
            </EmptyState>
            <ul v-else class="space-y-3">
              <li
                v-for="row in basket.items.value"
                :key="row.key"
                class="rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) p-3"
              >
                <div class="flex items-start gap-2">
                  <ItemIcon :item-id="row.itemId" :size="32" :alt="row.itemName" />
                  <div class="flex-1">
                    <div class="font-medium">{{ row.itemName }}<HQMark :hq="row.hq" /></div>
                    <div class="mt-1"><WorldBadge :world-name="row.sourceWorldName" /></div>
                    <div class="mt-1 text-sm">
                      {{ row.quantity }} × <GilAmount :value="row.buyPrice" />
                      <MutedText size="sm" class="ml-1">· {{ row.action }}</MutedText>
                    </div>
                  </div>
                  <DeleteButton @click="basket.remove(row.key)" />
                </div>
              </li>
            </ul>
          </div>

          <div v-if="savePanelOpen && session.isAuthenticated.value"
               class="border-t border-(--border) bg-(--bg-accent) p-4 space-y-3">
            <div>
              <label class="mb-1 block text-xs font-semibold uppercase tracking-wider text-(--text-muted)">{{ t('baskets.name') }}</label>
              <input
                v-model="saveName"
                type="text"
                maxlength="120"
                class="w-full rounded-(--radius-theme) border border-(--border) bg-(--bg) px-2 py-1 text-sm"
              />
            </div>
            <div>
              <label class="mb-1 block text-xs font-semibold uppercase tracking-wider text-(--text-muted)">{{ t('baskets.visibilityLabel') }}</label>
              <select
                v-model="saveVisibility"
                class="w-full rounded-(--radius-theme) border border-(--border) bg-(--bg) px-2 py-1 text-sm"
              >
                <option value="private">{{ t('baskets.private') }}</option>
                <option value="authenticated">{{ t('baskets.authenticated') }}</option>
                <option value="public">{{ t('baskets.public') }}</option>
              </select>
            </div>
            <div v-if="saveError" class="text-sm text-(--color-error)">{{ saveError }}</div>
            <div v-if="savedShareUrl">
              <MutedText size="sm">{{ t('baskets.shareLink') }}</MutedText>
              <code class="mt-1 block break-all rounded bg-(--bg) px-2 py-1 text-xs">{{ savedShareUrl }}</code>
              <SecondaryButton class="mt-2" @click="copyShare">{{ t('common.copyLink') }}</SecondaryButton>
            </div>
            <div class="flex gap-2">
              <SecondaryButton full-width @click="toggleSavePanel">{{ t('common.cancel') }}</SecondaryButton>
              <PrimaryButton full-width :disabled="saving || basket.count.value === 0" @click="save">
                <span v-if="saving">{{ t('baskets.saving') }}</span>
                <span v-else-if="savedShareUrl">{{ t('baskets.saveAnother') }}</span>
                <span v-else>{{ t('common.save') }}</span>
              </PrimaryButton>
            </div>
          </div>

          <footer class="border-t border-(--border) p-4">
            <div class="mb-3 flex items-center justify-between text-sm">
              <MutedText size="sm">{{ t('basket.estSpend') }}</MutedText>
              <GilAmount :value="basket.totalSpend.value" />
            </div>
            <div class="mb-3 flex items-center justify-between text-sm">
              <MutedText size="sm">{{ t('basket.totals.evPerHour') }}</MutedText>
              <GilAmount :value="basket.totalEvPerHour.value" />
            </div>
            <div class="flex flex-wrap gap-2">
              <SecondaryButton :disabled="basket.count.value === 0" @click="basket.clear">
                {{ t('common.clear') }}
              </SecondaryButton>
              <SecondaryButton
                v-if="session.isAuthenticated.value"
                :disabled="basket.count.value === 0"
                @click="toggleSavePanel"
              >
                <FontAwesomeIcon :icon="['fas', 'bookmark']" class="mr-2" />
                {{ savePanelOpen ? t('baskets.hideSave') : t('common.save') }}
              </SecondaryButton>
              <PrimaryButton class="ml-auto" @click="openPlanner">
                <FontAwesomeIcon :icon="['fas', 'route']" class="mr-2" />
                {{ t('basket.planCta') }}
              </PrimaryButton>
            </div>
            <div class="mt-3 flex items-center justify-between">
              <MutedText size="sm">
                {{ t('basket.plannerHint') }}
              </MutedText>
              <button
                type="button"
                class="ml-3 shrink-0 text-xs text-(--text-muted) underline hover:text-(--text)"
                @click="openBasketPage"
              >
                {{ $t('basket.fullViewLink') }}
              </button>
            </div>
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.2s ease;
}
.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}
</style>
