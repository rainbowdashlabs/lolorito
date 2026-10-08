/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { PerfPoint } from '@/api/dashboard'

const props = defineProps<{ points: PerfPoint[]; title: string }>()

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function themeColors() {
  const styles = window.getComputedStyle(document.documentElement)
  return {
    text: styles.getPropertyValue('--text').trim() || '#e6e6e6',
    border: styles.getPropertyValue('--border').trim() || '#3a3a3a',
    bar: styles.getPropertyValue('--color-warning').trim() || '#e0a44a',
    fontFamily: styles.getPropertyValue('font-family').trim() || 'inherit',
  }
}

function render() {
  if (!chart) return
  const { text, border, bar, fontFamily } = themeColors()
  const labelStyle = { color: text, fontSize: 14, fontFamily, fontWeight: 500 }
  const sorted = props.points.slice().sort((a, b) => a.capturedAt.localeCompare(b.capturedAt))
  const rows = sorted.map((p) => [p.capturedAt, p.valueMs])
  chart.setOption({
    textStyle: labelStyle,
    title: { text: props.title, textStyle: { ...labelStyle, fontSize: 15, fontWeight: 600 } },
    tooltip: { trigger: 'axis', textStyle: { fontFamily, fontSize: 14 } },
    grid: { left: 55, right: 20, top: 44, bottom: 34 },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: border } },
      axisLabel: { ...labelStyle, hideOverlap: true },
      splitLine: { show: true, lineStyle: { color: border, opacity: 0.35 } },
    },
    yAxis: {
      type: 'value',
      name: 'ms',
      nameTextStyle: labelStyle,
      axisLine: { lineStyle: { color: border } },
      axisLabel: labelStyle,
      splitLine: { lineStyle: { color: border, opacity: 0.3 } },
    },
    series: [
      {
        type: 'bar',
        data: rows,
        itemStyle: { color: bar },
        barMaxWidth: 20,
      },
    ],
  })
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  if (!chartEl.value) return
  chart = echarts.init(chartEl.value, undefined, { renderer: 'canvas' })
  render()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})

watch(() => props.points, render, { deep: true })
</script>

<template>
  <div ref="chartEl" class="h-64 w-full" />
</template>
