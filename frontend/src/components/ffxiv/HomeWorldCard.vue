/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NumberInput from '@/components/input/text/NumberInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import HourlySalesBars from '@/components/ffxiv/HourlySalesBars.vue'
import { worldActivity, type WorldActivity } from '@/api/trends'
import { useFilter } from '@/composables/useFilter'
import { useWorlds } from '@/composables/useWorlds'

/**
 * Home-world summary on the dashboard: where the user trades, the gil
 * they have to spend (stored as the offers budget), the last day of sales
 * on that world, and a shortcut into the planner with that budget.
 */
const { t } = useI18n()
const router = useRouter()
const filter = useFilter()
const worlds = useWorlds()
const activity = ref<WorldActivity | null>(null)

const homeWorldId = computed(() => filter.current.value?.worldId ?? 0)
const home = computed(() => (homeWorldId.value > 0 ? worlds.worldById(homeWorldId.value) : null))
const budget = computed(() => filter.current.value?.budget ?? 0)

async function loadActivity() {
  if (homeWorldId.value <= 0) return
  try {
    activity.value = await worldActivity(homeWorldId.value)
  } catch {
    activity.value = null
  }
}

function onBudget(v: number | undefined) {
  filter.patchDebounced({ budget: Math.max(0, Math.floor(v ?? 0)) })
}

function planRun() {
  router.push({ name: 'planner', query: budget.value > 0 ? { budget: String(budget.value) } : {} })
}

onMounted(async () => {
  await Promise.all([filter.load(), worlds.load()])
  await loadActivity()
})

watch(homeWorldId, loadActivity)
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ t('homeWorld.title') }}</SectionHeader>
    <MutedText v-if="!home?.world" size="sm">{{ t('homeWorld.notSet') }}</MutedText>
    <div v-else class="mt-3 grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
      <div class="space-y-3">
        <div>
          <div class="text-lg font-semibold">{{ home.world.name }}</div>
          <MutedText size="sm">{{ home.dataCenter }} · {{ home.region }}</MutedText>
        </div>
        <div>
          <FieldLabel>{{ t('homeWorld.gilInPocket') }}</FieldLabel>
          <NumberInput
            :model-value="budget || undefined"
            :min="0"
            :step="10000"
            :placeholder="t('offersFilter.unbounded')"
            class="mt-1 w-full"
            @update:model-value="onBudget"
          />
        </div>
        <PrimaryButton @click="planRun">{{ t('homeWorld.planRun') }}</PrimaryButton>
      </div>
      <div>
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <FieldLabel>{{ t('homeWorld.last24h') }}</FieldLabel>
          <MutedText v-if="activity" size="sm">
            {{ t('homeWorld.totals', { units: activity.totalUnits.toLocaleString() }) }}
            · <GilAmount :value="activity.totalGil" />
          </MutedText>
        </div>
        <HourlySalesBars v-if="activity" :hours="activity.hours" class="mt-2" />
        <MutedText v-else size="sm">{{ t('homeWorld.noActivity') }}</MutedText>
      </div>
    </div>
  </NeutralContainer>
</template>
