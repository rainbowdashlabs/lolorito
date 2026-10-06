/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import type { StopChange } from '@/util/planDiff'

/** What the last replan changed, one line per affected stop, plus the objective delta. */
defineProps<{ changes: StopChange[]; objectiveDelta: number }>()

const { t } = useI18n()
</script>

<template>
  <div class="mb-3 rounded-(--radius-theme) border border-(--border) bg-(--bg-accent) px-3 py-2 text-sm print:hidden">
    <div class="font-semibold">
      {{ t('replanDiff.title') }}
      <span :class="objectiveDelta >= 0 ? 'text-(--color-success)' : 'text-(--color-error)'">
        {{ objectiveDelta >= 0 ? '+' : '' }}<GilAmount :value="objectiveDelta" />
      </span>
    </div>
    <MutedText v-if="changes.length === 0" size="sm">{{ t('replanDiff.noStopChanges') }}</MutedText>
    <ul v-else class="mt-1 space-y-0.5">
      <li v-for="c in changes" :key="`${c.kind}-${c.worldId}`" class="flex flex-wrap gap-x-2">
        <span>{{ t(`replanDiff.${c.kind}`, { world: c.worldName }) }}</span>
        <span :class="c.evDelta >= 0 ? 'text-(--color-success)' : 'text-(--color-error)'">
          {{ t('replanDiff.ev') }} {{ c.evDelta >= 0 ? '+' : '' }}<GilAmount :value="c.evDelta" />
        </span>
        <MutedText size="sm">
          {{ t('replanDiff.spend') }} {{ c.spendDelta >= 0 ? '+' : '' }}<GilAmount :value="c.spendDelta" />
        </MutedText>
      </li>
    </ul>
  </div>
</template>
