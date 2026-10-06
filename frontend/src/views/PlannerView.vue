/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PlannerParamsPanel from '@/components/ffxiv/PlannerParamsPanel.vue'
import PlanResults from '@/components/ffxiv/PlanResults.vue'
import ReplanDiffNote from '@/components/ffxiv/ReplanDiffNote.vue'
import { plannerApi } from '@/api'
import type { Plan, PlanRequest } from '@/api/planner'
import { loadPlannerParams, savePlannerParams } from '@/api/auth'
import { useFilter } from '@/composables/useFilter'
import { pushToast } from '@/composables/useToasts'
import { diffStops, type ReplanDiff } from '@/util/planDiff'

// Params exposed in the form; `homeWorld` + `refreshHours` are optional so
// the user can override them per-plan without touching their saved filter.
type Params = Required<Omit<PlanRequest, 'homeWorld' | 'refreshHours'>> & {
  homeWorld?: number
  refreshHours?: number
}

const { t } = useI18n()
const filter = useFilter()
const route = useRoute()
const plan = ref<Plan | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const completed = ref<Set<number>>(new Set())

const form = ref<Params>({
  budget: 1_000_000,
  inventorySlots: 140,
  attentionBudgetHours: 8,
  attentionFraction: 0.25,
  hopWeightGilPerSecond: 100,
  maxWorlds: 5,
  candidateTopK: 250,
  retainerSlots: 20,
  retainerListingSlots: 40,
  retainerListingStackTarget: 20,
  retainerAttentionFraction: 0.05,
  retainerShelfHoursThreshold: 6,
  allowCrafts: false,
  allowDesynth: false,
})

const replanning = ref(false)
const replanNote = ref<string | null>(null)
const replanDiff = ref<ReplanDiff | null>(null)

async function planRun() {
  loading.value = true
  error.value = null
  // Fresh plan run — wipe the step-tracker state so a stale set of
  // completed worlds doesn't leak into the new run.
  completed.value = new Set()
  completedWorldSet.value = new Set()
  completedSpend.value = 0
  completedQty.value = 0
  replanNote.value = null
  replanDiff.value = null
  try {
    plan.value = await plannerApi.plan({ ...form.value })
    // Fire-and-forget — a network hiccup here shouldn't fail the plan
    // action, the user gets their result and next visit picks up the
    // previously-saved form.
    savePlannerParams(form.value).catch(() => {})
  } catch (e: unknown) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

function extractError(e: unknown): string {
  const obj = e as { response?: { data?: { error?: string } }; message?: string }
  return obj?.response?.data?.error ?? obj?.message ?? String(e)
}

// Persistent record of stops the user has already walked through. Keyed
// by worldId (not array index) so a replan that shortens the stop list
// keeps this data valid — the previous implementation stored indices,
// cleared them after each replan, and the backend then had no reason to
// keep excluding the earlier stops. Result: marking the new first stop
// done brought the old first stop back.
const completedWorldSet = ref<Set<number>>(new Set())
const completedSpend = ref<number>(0)
const completedQty = ref<number>(0)

function completedWorldIds(): number[] {
  return Array.from(completedWorldSet.value)
}

function spentBudget(): number {
  return completedSpend.value
}

function usedInventory(): number {
  return completedQty.value
}

async function toggleDone(index: number) {
  if (!plan.value) return
  const stop = plan.value.stops[index]
  if (!stop) return
  const already = completedWorldSet.value.has(stop.worldId)
  // Track slots consumed by completed stops (not raw units) — the
  // backend's remaining-inventory calc is in slot units too.
  if (already) {
    completedWorldSet.value.delete(stop.worldId)
    completedSpend.value = Math.max(0, completedSpend.value - stop.buyCost)
    completedQty.value = Math.max(0, completedQty.value - stop.totalSlots)
  } else {
    completedWorldSet.value.add(stop.worldId)
    completedSpend.value += stop.buyCost
    completedQty.value += stop.totalSlots
  }
  // Nudge Vue's reactivity for the Set.
  completedWorldSet.value = new Set(completedWorldSet.value)
  // Keep `completed` (used by the PlanResults component to render the
  // checkmarks) in sync — it still expects a Set of indices.
  const indexSet = new Set<number>()
  plan.value.stops.forEach((s, i) => {
    if (completedWorldSet.value.has(s.worldId)) indexSet.add(i)
  })
  completed.value = indexSet
  await requestReplan()
}

async function requestReplan() {
  if (!plan.value || completed.value.size === 0) {
    replanNote.value = null
    replanDiff.value = null
    return
  }
  replanning.value = true
  try {
    const previous = plan.value
    const next = await plannerApi.replan({
      ...form.value,
      completedWorldIds: completedWorldIds(),
      spentBudget: spentBudget(),
      usedInventory: usedInventory(),
    })
    const changes = diffStops(previous.stops, next.stops, completedWorldSet.value)
    replanNote.value = null
    if (changes.length > 0) {
      const delta = Math.round(next.objective - previous.objective)
      replanDiff.value = { changes, objectiveDelta: delta }
      const dropped = changes.filter((c) => c.kind === 'dropped').map((c) => c.worldName)
      const added = changes.filter((c) => c.kind === 'added').map((c) => c.worldName)
      const parts: string[] = []
      if (dropped.length) parts.push(t('toasts.replanDropped', { names: dropped.join(', ') }))
      if (added.length) parts.push(t('toasts.replanAdded', { names: added.join(', ') }))
      const detail = parts.length
        ? parts.join(' · ')
        : t('replanDiff.stopsAdjusted', { count: changes.length }, changes.length)
      const deltaLabel = `${delta >= 0 ? '+' : ''}${delta.toLocaleString()}g`
      pushToast(t('toasts.planReplan', { detail, delta: deltaLabel }), delta >= 0 ? 'success' : 'warning')
    } else {
      replanDiff.value = null
    }
    plan.value = next
    // Rebuild the visible checkmark set against the new plan's stop
    // ordering. Persistent state (world ids, spend, qty) is preserved
    // so the backend keeps excluding earlier stops on the next replan.
    const indexSet = new Set<number>()
    next.stops.forEach((s: { worldId: number }, i: number) => {
      if (completedWorldSet.value.has(s.worldId)) indexSet.add(i)
    })
    completed.value = indexSet
  } catch (e: unknown) {
    replanDiff.value = null
    replanNote.value = t('planner.replanFailed', { error: extractError(e) })
  } finally {
    replanning.value = false
  }
}

function print() {
  window.print()
}

onMounted(async () => {
  await filter.load()
  // Restore the caller's last-used planner form. The endpoint returns
  // null when nothing is saved yet — in that case we keep the built-in
  // defaults so first-time visitors still get something sensible.
  const saved = await loadPlannerParams<Params>()
  if (saved) {
    form.value = { ...form.value, ...saved }
  }
  const budgetFromLink = Number(route.query.budget)
  if (budgetFromLink > 0) form.value = { ...form.value, budget: Math.floor(budgetFromLink) }
})

</script>

<template>
  <PageHeader
    :title="t('planner.title')"
    :subtitle="t('planner.subtitle')"
  />

  <div class="grid grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-[320px_minmax(0,1fr)] print:block">
    <aside class="print:hidden">
      <PlannerParamsPanel v-model:params="form" :loading="loading" @plan="planRun" />
    </aside>

    <section>
      <ReplanDiffNote v-if="replanDiff" :changes="replanDiff.changes" :objective-delta="replanDiff.objectiveDelta" />
      <div v-if="replanNote" class="mb-3 rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) px-3 py-2 print:hidden">
        <MutedText size="sm">{{ replanNote }}</MutedText>
      </div>
      <div v-if="loading" class="py-8 text-center">
        <Spinner size="lg" />
        <div class="mt-2"><MutedText size="sm">{{ t('planner.solving') }}</MutedText></div>
      </div>
      <Alert v-else-if="error" variant="error">
        {{ t('planner.failedToPlan', { error }) }}
      </Alert>
      <div v-else-if="!plan" class="print:hidden">
        <EmptyState>
          {{ t('planner.empty') }}
        </EmptyState>
      </div>
      <div
        v-else-if="plan.stops.length === 0 && plan.retainerBasket.length === 0
          && plan.crafts.length === 0 && plan.desynths.length === 0"
      >
        <EmptyState>
          {{ t('planner.noPositiveEv') }}
        </EmptyState>
      </div>
      <PlanResults
        v-else
        :plan="plan"
        :completed="completed"
        :hop-weight-gil-per-second="form.hopWeightGilPerSecond"
        @toggle-done="toggleDone"
        @print="print"
      />
    </section>
  </div>
</template>
