/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import GilAmount from '@/components/ffxiv/GilAmount.vue'
import AdversaryBadges from '@/components/ffxiv/AdversaryBadges.vue'
import LambdaCurveChart from '@/components/ffxiv/LambdaCurveChart.vue'
import type { ItemModelDto } from '@/api/items'

const { t } = useI18n()

defineProps<{
  model: ItemModelDto | null
}>()
</script>

<template>
  <NeutralContainer>
    <SectionHeader>{{ t('item.marketModel') }}</SectionHeader>
    <div v-if="model" class="mt-3 space-y-2 text-sm">
      <div class="flex items-center gap-2">
        <SuccessBadge v-if="model.sufficient">{{ t('item.sufficient') }}</SuccessBadge>
        <ErrorBadge v-else>{{ t('item.insufficient') }}</ErrorBadge>
        <InfoBadge
          v-if="model.pooled"
          :title="t('item.pooledHint')"
        >
          {{ t('adversary.pooled') }}
        </InfoBadge>
        <MutedText size="sm">{{ t('item.sampleCount', { count: model.sampleCount }, model.sampleCount) }}</MutedText>
      </div>
      <div>{{ t('item.expectedSell') }} <GilAmount :value="model.expectedPrice" /></div>
      <div>{{ t('item.median') }} <GilAmount :value="model.medianPrice" /></div>
      <div>{{ t('item.sigmaLog') }} {{ model.sigma.toFixed(3) }}</div>
      <div class="mt-3">
        <MutedText size="sm">{{ t('item.saleRateCurve') }}</MutedText>
        <div class="mt-1">
          <LambdaCurveChart
            :lambda-aggressive="model.lambdaAggressive"
            :lambda-median="model.lambdaMedian"
            :lambda-above="model.lambdaAbove"
          />
        </div>
      </div>
      <div class="mt-3">
        <MutedText size="sm">{{ t('item.adversaryFlags') }}</MutedText>
        <div class="mt-1">
          <AdversaryBadges
            :lambda-undercut="model.lambdaUndercut"
            :ghost-fraction="model.ghostFraction"
            :model-sufficient="model.sufficient"
            :hide-insufficient="true"
          />
        </div>
      </div>
    </div>
    <div v-else class="mt-3">
      <MutedText size="sm">{{ t('item.noModelFitted') }}</MutedText>
    </div>
  </NeutralContainer>
</template>
