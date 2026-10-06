/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import AlertRuleForm from '@/components/ffxiv/AlertRuleForm.vue'
import { alertsApi, itemSearchApi } from '@/api'
import { pushToast } from '@/composables/useToasts'
import type { AlertRule, CreateRequest } from '@/api/alerts'
import { useWorlds } from '@/composables/useWorlds'

const { t } = useI18n()
const worlds = useWorlds()
const rules = ref<AlertRule[]>([])
const itemNames = ref<Record<number, string>>({})
function itemLabel(id: number): string {
  return itemNames.value[id] ?? t('common.itemNumber', { id })
}
const loading = ref(true)
const error = ref<string | null>(null)
const submitting = ref(false)
const submitError = ref<string | null>(null)

const worldsIndex = computed(() => {
  const list: { id: number; name: string }[] = []
  for (const region of worlds.regions.value) {
    for (const dc of region.dataCenters) {
      for (const w of dc.worlds) list.push({ id: w.id, name: w.name })
    }
  }
  return list
})

const dcIndex = computed(() => {
  const seen = new Map<number, string>()
  for (const region of worlds.regions.value) {
    for (const dc of region.dataCenters) seen.set(dc.id, dc.name)
  }
  return seen
})

async function refresh() {
  loading.value = true
  error.value = null
  try {
    rules.value = await alertsApi.list()
    const ids = [...new Set(rules.value.map((r) => r.itemId))]
    const missing = ids.filter((id) => !(id in itemNames.value))
    if (missing.length > 0) {
      const resolved = await itemSearchApi.namesByIds(missing)
      const patch: Record<number, string> = { ...itemNames.value }
      for (const [k, v] of Object.entries(resolved)) patch[Number(k)] = v
      itemNames.value = patch
    }
  } catch (e: unknown) {
    error.value = extract(e)
  } finally {
    loading.value = false
  }
}

function extract(e: unknown): string {
  const obj = e as { response?: { data?: { error?: string } }; message?: string }
  return obj?.response?.data?.error ?? obj?.message ?? String(e)
}

async function submit(payload: CreateRequest) {
  submitting.value = true
  submitError.value = null
  try {
    const created = await alertsApi.create(payload)
    rules.value = [created, ...rules.value]
  } catch (e: unknown) {
    submitError.value = extract(e)
  } finally {
    submitting.value = false
  }
}

async function remove(id: string) {
  try {
    await alertsApi.remove(id)
    rules.value = rules.value.filter((r) => r.id !== id)
  } catch (e: unknown) {
    error.value = extract(e)
  }
}

async function toggle(rule: AlertRule) {
  try {
    const next = await alertsApi.setEnabled(rule.id, !rule.enabled)
    rules.value = rules.value.map((r) => (r.id === next.id ? next : r))
  } catch (e: unknown) {
    error.value = extract(e)
  }
}

async function testRule(rule: AlertRule) {
  try {
    await alertsApi.test(rule.id)
    pushToast(t('toasts.alertSent', { name: itemLabel(rule.itemId) }), 'success')
  } catch (e: unknown) {
    pushToast(t('toasts.alertFailed', { error: extract(e) }), 'error')
  }
}

function scopeLabel(r: AlertRule): string {
  if (r.worldId != null) {
    return worldsIndex.value.find((x) => x.id === r.worldId)?.name ?? t('alerts.worldFallback', { id: r.worldId })
  }
  if (r.dataCenterId != null) {
    return dcIndex.value.get(r.dataCenterId) ?? t('alerts.dcFallback', { id: r.dataCenterId })
  }
  return t('alerts.scopeUnknown')
}

function hqLabel(r: AlertRule): string {
  if (r.hq === null) return t('alerts.eitherTag')
  return r.hq ? t('alerts.hqTag') : t('alerts.nqTag')
}

onMounted(async () => {
  await worlds.load()
  await refresh()
})
</script>

<template>
  <PageHeader :title="t('alerts.title')" :subtitle="t('alerts.subtitle')" />

  <div class="grid gap-6 lg:grid-cols-[360px_1fr]">
    <AlertRuleForm :submitting="submitting" :error="submitError" @submit="submit" />

    <section>
      <div v-if="loading" class="py-8 text-center">
        <Spinner size="lg" />
      </div>
      <Alert v-else-if="error" variant="error">{{ t('common.failedToLoad', { error }) }}</Alert>
      <EmptyState v-else-if="rules.length === 0">
        {{ t('alerts.empty') }}
      </EmptyState>
      <ul v-else class="space-y-3">
        <li
          v-for="rule in rules"
          :key="rule.id"
          class="rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) p-4"
        >
          <div class="flex flex-wrap items-start gap-3">
            <div class="min-w-0 flex-1">
              <div class="font-semibold">
                {{ itemLabel(rule.itemId) }}
                <MutedText size="sm" class="ml-1">
                  · {{ scopeLabel(rule) }} · {{ hqLabel(rule) }} · {{ rule.kind === 'price_below' ? t('alerts.below') : t('alerts.above') }}
                  <GilAmount :value="rule.thresholdPrice" />
                </MutedText>
              </div>
              <MutedText size="sm">
                {{ t('alerts.cooldownMin', { n: rule.cooldownMinutes }) }} ·
                <span v-if="rule.lastTriggeredAt">{{ t('alerts.lastFired', { when: rule.lastTriggeredAt.slice(0, 16).replace('T', ' ') }) }}</span>
                <span v-else>{{ t('alerts.neverFired') }}</span>
              </MutedText>
            </div>
            <div class="flex gap-2">
              <SecondaryButton compact @click="testRule(rule)">{{ t('alerts.test') }}</SecondaryButton>
              <SecondaryButton compact @click="toggle(rule)">
                {{ rule.enabled ? t('alerts.pause') : t('alerts.resume') }}
              </SecondaryButton>
              <DeleteButton @click="remove(rule.id)" />
            </div>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>
