/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import { useBasket } from '@/composables/useBasket'
import { useSession } from '@/composables/useSession'
import { basketsApi } from '@/api'
import type { BasketVisibility } from '@/api/baskets'
import { pushToast } from '@/composables/useToasts'

const { t } = useI18n()
const basket = useBasket()
const session = useSession()

defineEmits<{ planRun: []; clearBasket: [] }>()

const totalUnits = computed(() => basket.items.value.reduce((sum, b) => sum + b.quantity, 0))

const savePanelOpen = ref(false)
const saveName = ref('')
const saveVisibility = ref<BasketVisibility>('private')
const saving = ref(false)
const savedShareUrl = ref<string | null>(null)
const saveError = ref<string | null>(null)

function toggleSavePanel() {
  savePanelOpen.value = !savePanelOpen.value
  savedShareUrl.value = null
  saveError.value = null
  if (savePanelOpen.value && !saveName.value) {
    saveName.value = `Basket ${new Date().toISOString().slice(0, 10)}`
  }
}

async function save() {
  if (saving.value) return
  saving.value = true
  saveError.value = null
  try {
    const saved = await basketsApi.create({
      name: saveName.value.trim() || 'Untitled',
      visibility: saveVisibility.value,
      items: basket.items.value.map((it) => ({
        key: it.key,
        itemId: it.itemId,
        itemName: it.itemName,
        hq: it.hq,
        sourceWorldId: it.sourceWorldId,
        sourceWorldName: it.sourceWorldName,
        quantity: it.quantity,
        buyPrice: it.buyPrice,
        action: it.action,
        evPerHour: it.evPerHour,
        addedAt: it.addedAt,
      })),
    })
    savedShareUrl.value =
      saved.visibility === 'private' ? null : `${window.location.origin}/baskets/shared/${saved.shareToken}`
    pushToast(t('common.save'), 'success')
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
  <div class="space-y-4">
    <NeutralContainer>
      <SectionHeader>{{ $t('offers.col.expectedNet') }}</SectionHeader>
      <div class="mt-3 space-y-2 text-sm">
        <div class="flex items-center justify-between">
          <MutedText size="sm">{{ $t('basket.totals.items') }}</MutedText>
          <span class="font-medium">{{ basket.count.value }}</span>
        </div>
        <div class="flex items-center justify-between">
          <MutedText size="sm">{{ $t('basket.totals.units') }}</MutedText>
          <span class="font-medium">{{ totalUnits }}</span>
        </div>
        <div class="flex items-center justify-between">
          <MutedText size="sm">{{ $t('basket.totals.spend') }}</MutedText>
          <GilAmount :value="basket.totalSpend.value" />
        </div>
        <div class="flex items-center justify-between">
          <MutedText size="sm">{{ $t('basket.totals.evPerHour') }}</MutedText>
          <GilAmount :value="basket.totalEvPerHour.value" />
        </div>
      </div>
      <div class="mt-4 space-y-2">
        <PrimaryButton full-width :disabled="basket.count.value === 0" @click="$emit('planRun')">
          <FontAwesomeIcon :icon="['fas', 'route']" class="mr-2" />
          {{ $t('basket.planCta') }}
        </PrimaryButton>
        <SecondaryButton
          v-if="session.isAuthenticated.value"
          full-width
          :disabled="basket.count.value === 0"
          @click="toggleSavePanel"
        >
          <FontAwesomeIcon :icon="['fas', 'bookmark']" class="mr-2" />
          {{ savePanelOpen ? $t('baskets.hideSave') : $t('baskets.savePanel') }}
        </SecondaryButton>
        <SecondaryButton
          v-if="basket.count.value > 0"
          full-width
          class="!text-(--color-error)"
          @click="$emit('clearBasket')"
        >
          <FontAwesomeIcon :icon="['fas', 'trash']" class="mr-2" />
          {{ $t('basket.clearAll') }}
        </SecondaryButton>
      </div>
    </NeutralContainer>

    <NeutralContainer v-if="savePanelOpen && session.isAuthenticated.value">
      <SectionHeader>{{ $t('baskets.saveTitle') }}</SectionHeader>
      <div class="mt-3 space-y-3 text-sm">
        <div>
          <FieldLabel>{{ $t('baskets.name') }}</FieldLabel>
          <input
            v-model="saveName"
            type="text"
            maxlength="120"
            class="mt-1 w-full rounded-(--radius-theme) border border-(--border) bg-(--bg) px-2 py-1 text-sm"
          />
        </div>
        <div>
          <FieldLabel>{{ $t('baskets.visibility') }}</FieldLabel>
          <SelectInput v-model="saveVisibility" class="mt-1 w-full">
            <option value="private">{{ $t('baskets.private') }}</option>
            <option value="authenticated">{{ $t('baskets.authenticated') }}</option>
            <option value="public">{{ $t('baskets.public') }}</option>
          </SelectInput>
        </div>
        <div v-if="saveError" class="text-sm text-(--color-error)">{{ saveError }}</div>
        <div v-if="savedShareUrl">
          <MutedText size="sm">{{ $t('baskets.shareLink') }}</MutedText>
          <code class="mt-1 block break-all rounded bg-(--bg) px-2 py-1 text-xs">{{ savedShareUrl }}</code>
          <SecondaryButton class="mt-2" @click="copyShare">{{ $t('common.copy') }}</SecondaryButton>
        </div>
        <PrimaryButton full-width :disabled="saving || basket.count.value === 0" @click="save">
          <span v-if="saving">{{ $t('baskets.saving') }}</span>
          <span v-else-if="savedShareUrl">{{ $t('baskets.saveAnother') }}</span>
          <span v-else>{{ $t('baskets.savePanel') }}</span>
        </PrimaryButton>
      </div>
    </NeutralContainer>
  </div>
</template>
