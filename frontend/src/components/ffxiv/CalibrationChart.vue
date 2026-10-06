/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts'
import type { CalibrationHistoryPoint } from '@/api/dashboard'

const { t, locale } = useI18n()
const props = defineProps<{ points: CalibrationHistoryPoint[] }>()

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function themeColors() {
  const styles = window.getComputedStyle(document.documentElement)
  const text = styles.getPropertyValue('--text').trim() || '#e6e6e6'
  const border = styles.getPropertyValue('--border').trim() || '#3a3a3a'
  const accent = styles.getPropertyValue('--color-info').trim() || '#4d9dff'
  const warn = styles.getPropertyValue('--color-warning').trim() || '#e0a44a'
  const fontFamily = styles.getPropertyValue('font-family').trim() || 'inherit'
  return { text, border, accent, warn, fontFamily }
}

function render() {
  if (!chart) return
  const { text, border, accent, warn, fontFamily } = themeColors()
  const rows = props.points.map((p) => [p.capturedAt, Number((Math.expm1(p.logRatioMean) * 100).toFixed(2))])
  const samples = props.points.map((p) => [p.capturedAt, p.sampleCount])
  const labelStyle = { color: text, fontSize: 14, fontFamily, fontWeight: 500 }
  chart.setOption({
    textStyle: labelStyle,
    tooltip: { trigger: 'axis', textStyle: { fontFamily, fontSize: 14 } },
    legend: {
      data: [t('dashboard.chart.biasPct'), t('dashboard.chart.sampleCount')],
      top: 4,
      textStyle: labelStyle,
    },
    grid: { left: 60, right: 60, top: 40, bottom: 34 },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: border } },
      axisLabel: labelStyle,
      splitLine: { show: true, lineStyle: { color: border, opacity: 0.35 } },
    },
    yAxis: [
      {
        type: 'value',
        name: t('dashboard.chart.biasPct'),
        nameTextStyle: labelStyle,
        axisLine: { lineStyle: { color: border } },
        axisLabel: { ...labelStyle, formatter: '{value}%' },
        splitLine: { lineStyle: { color: border, opacity: 0.3 } },
      },
      {
        type: 'value',
        name: t('dashboard.chart.samples'),
        nameTextStyle: labelStyle,
        axisLine: { lineStyle: { color: border } },
        axisLabel: labelStyle,
      },
    ],
    series: [
      {
        name: t('dashboard.chart.biasPct'),
        type: 'line',
        data: rows,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 3.5, color: accent },
        itemStyle: { color: accent },
      },
      {
        name: t('dashboard.chart.sampleCount'),
        type: 'line',
        yAxisIndex: 1,
        data: samples,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 3, color: warn, opacity: 0.95 },
        itemStyle: { color: warn },
      },
    ],
  })
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  if (!chartEl.value) return
  // Canvas renders label text with the same font-smoothing/antialiasing as
  // the surrounding page; SVG picked up its own less-crisp defaults that
  // made labels look grey next to normal body text.
  chart = echarts.init(chartEl.value, undefined, { renderer: 'canvas' })
  render()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})

watch(() => props.points, render, { deep: true })
watch(locale, render)
</script>

<template>
  <div ref="chartEl" class="h-72 w-full" />
</template>
