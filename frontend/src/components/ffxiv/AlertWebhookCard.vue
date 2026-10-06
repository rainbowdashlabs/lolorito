/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import { get, put } from '@/api/webhook'
import { pushToast } from '@/composables/useToasts'

const { t } = useI18n()
const url = ref('')
const loading = ref(false)
const busy = ref(false)

async function refresh() {
  loading.value = true
  try {
    url.value = await get()
  } catch (e: unknown) {
    const obj = e as { message?: string }
    pushToast(t('webhook.loadFailed', { error: obj?.message ?? 'unknown' }), 'error')
  } finally {
    loading.value = false
  }
}

async function save() {
  busy.value = true
  try {
    url.value = await put(url.value)
    pushToast(t('webhook.saved'), 'success')
  } catch (e: unknown) {
    const obj = e as { response?: { data?: { error?: string } }; message?: string }
    pushToast(t('webhook.saveFailed', { error: obj?.response?.data?.error ?? obj?.message ?? 'unknown' }), 'error')
  } finally {
    busy.value = false
  }
}

async function clear() {
  busy.value = true
  try {
    url.value = await put('')
    pushToast(t('webhook.cleared'), 'info')
  } catch (e: unknown) {
    const obj = e as { message?: string }
    pushToast(t('webhook.saveFailed', { error: obj?.message ?? 'unknown' }), 'error')
  } finally {
    busy.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ $t('webhook.title') }}</SectionHeader>
    <MutedText size="sm">{{ $t('webhook.hint') }}</MutedText>

    <div v-if="loading" class="mt-3">
      <MutedText size="sm">{{ $t('common.loading') }}</MutedText>
    </div>
    <div v-else class="mt-4 space-y-2">
      <FieldLabel>{{ $t('webhook.urlLabel') }}</FieldLabel>
      <TextInput v-model="url" :placeholder="$t('webhook.placeholder')" />
      <div class="flex gap-2">
        <PrimaryButton :disabled="busy" @click="save">
          {{ $t('common.save') }}
        </PrimaryButton>
        <SecondaryButton v-if="url" :disabled="busy" @click="clear">
          {{ $t('webhook.clear') }}
        </SecondaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
