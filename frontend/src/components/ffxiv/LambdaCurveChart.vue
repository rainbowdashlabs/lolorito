/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * Three-anchor sale-rate curve — the fitter estimates λ(r) at ratios
 * 0.95 / 1.00 / 1.05, and the solver interpolates between them
 * (log-linear) at any listing price. This renders those three anchors
 * as a compact SVG sparkline with the interpolated middle segment,
 * so the reader can see "aggressive vs above-median" at a glance
 * without having to compare three scalar tiles.
 *
 * <p>SVG rather than echarts because it's a three-point curve — the
 * whole component fits in a template block and doesn't pay for a
 * chart-library instance on every item detail view.
 */
const { t } = useI18n()

const props = defineProps<{
  lambdaAggressive: number
  lambdaMedian: number
  lambdaAbove: number
}>()

const W = 260
const H = 96
const PAD_X = 32
const PAD_TOP = 12
const PAD_BOT = 24

const anchors = computed(() => [
  { ratio: 0.95, lambda: props.lambdaAggressive, label: '0.95×' },
  { ratio: 1.0, lambda: props.lambdaMedian, label: '1.00×' },
  { ratio: 1.05, lambda: props.lambdaAbove, label: '1.05×' },
])

const yMax = computed(() => {
  const values = anchors.value.map((a) => a.lambda)
  const max = Math.max(...values)
  // Guard against a flat-zero curve — draw the axis, keep the anchors
  // visible on the baseline rather than collapsing the plot.
  return max > 0 ? max * 1.1 : 1
})

function xFor(ratio: number): number {
  // Anchors span 0.95..1.05 — a fixed 0.1 window.
  return PAD_X + ((ratio - 0.95) / 0.1) * (W - PAD_X * 2)
}

function yFor(value: number): number {
  return H - PAD_BOT - (value / yMax.value) * (H - PAD_TOP - PAD_BOT)
}

const points = computed(() =>
  anchors.value.map((a) => ({
    x: xFor(a.ratio),
    y: yFor(a.lambda),
    ...a,
  })),
)

const pathD = computed(() => {
  const [p0, p1, p2] = points.value
  return `M ${p0.x} ${p0.y} L ${p1.x} ${p1.y} L ${p2.x} ${p2.y}`
})

const shadedArea = computed(() => {
  const [p0, p1, p2] = points.value
  const baseline = H - PAD_BOT
  return `M ${p0.x} ${baseline} L ${p0.x} ${p0.y} L ${p1.x} ${p1.y} L ${p2.x} ${p2.y} L ${p2.x} ${baseline} Z`
})
</script>

<template>
  <div class="w-full">
    <svg
      :viewBox="`0 0 ${W} ${H}`"
      class="w-full max-w-[260px]"
      preserveAspectRatio="xMinYMid meet"
      role="img"
      :aria-label="t('item.lambdaAria')"
    >
      <!-- baseline / axes -->
      <line
        :x1="PAD_X"
        :x2="W - PAD_X"
        :y1="H - PAD_BOT"
        :y2="H - PAD_BOT"
        stroke="var(--border)"
        stroke-width="1"
      />
      <line
        :x1="PAD_X"
        :x2="PAD_X"
        :y1="PAD_TOP"
        :y2="H - PAD_BOT"
        stroke="var(--border)"
        stroke-width="1"
      />
      <!-- median ratio marker -->
      <line
        :x1="xFor(1.0)"
        :x2="xFor(1.0)"
        :y1="PAD_TOP"
        :y2="H - PAD_BOT"
        stroke="var(--border)"
        stroke-width="1"
        stroke-dasharray="2 3"
      />
      <!-- shaded area under the curve -->
      <path :d="shadedArea" fill="var(--color-info)" fill-opacity="0.15" />
      <!-- curve itself -->
      <path :d="pathD" fill="none" stroke="var(--color-info)" stroke-width="2" stroke-linejoin="round" />
      <!-- anchor points -->
      <g v-for="p in points" :key="p.ratio">
        <circle :cx="p.x" :cy="p.y" r="3.5" fill="var(--color-info)" />
        <text
          :x="p.x"
          :y="H - 6"
          text-anchor="middle"
          class="fill-(--text-muted)"
          style="font-size: 10px"
        >
          {{ p.label }}
        </text>
      </g>
      <!-- axis label -->
      <text
        :x="6"
        :y="PAD_TOP + 6"
        text-anchor="start"
        class="fill-(--text-muted)"
        style="font-size: 10px"
      >
        {{ t('item.lambdaAxis') }}
      </text>
    </svg>
    <div class="mt-1 grid grid-cols-3 gap-2 text-xs text-(--text-muted)">
      <div class="text-center">λ(0.95) {{ lambdaAggressive.toFixed(3) }}</div>
      <div class="text-center">λ(1.00) {{ lambdaMedian.toFixed(3) }}</div>
      <div class="text-center">λ(1.05) {{ lambdaAbove.toFixed(3) }}</div>
    </div>
  </div>
</template>
