/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts'
import type { SalesBucket, ItemModelDto } from '@/api/items'

const { t, locale } = useI18n()
const props = defineProps<{ buckets: SalesBucket[]; model?: ItemModelDto | null }>()

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function themeColors() {
  const styles = window.getComputedStyle(document.documentElement)
  const text = styles.getPropertyValue('--text').trim() || '#e6e6e6'
  const border = styles.getPropertyValue('--border').trim() || '#3a3a3a'
  const success = styles.getPropertyValue('--color-success').trim() || '#5abf7b'
  const info = styles.getPropertyValue('--color-info').trim() || '#4d9dff'
  const warning = styles.getPropertyValue('--color-warning').trim() || '#f0a040'
  const fontFamily = styles.getPropertyValue('font-family').trim() || 'inherit'
  return { text, border, success, info, warning, fontFamily }
}

/**
 * Compute the ±1σ fitted-price band in linear-price space. The Java
 * fitter stores μ and σ on log-scale (right-truncated lognormal),
 * so linear band = [median / exp(σ), median · exp(σ)] where
 * median = exp(μ). We prefer the returned `medianPrice` over
 * re-computing exp(μ) because the backend already deals with the
 * right-truncation adjustment.
 */
function modelBand(model: ItemModelDto | null | undefined): [number, number] | null {
  if (!model || !model.sufficient) return null
  const spread = Math.exp(model.sigma)
  if (!Number.isFinite(spread) || spread <= 0) return null
  const low = model.medianPrice / spread
  const high = model.medianPrice * spread
  if (!Number.isFinite(low) || !Number.isFinite(high) || low <= 0) return null
  return [low, high]
}

type ThemeColors = ReturnType<typeof themeColors>
type LabelStyle = { color: string; fontSize: number; fontFamily: string; fontWeight: number }

function buildAxes(labelStyle: LabelStyle, border: string, priceRange: [number, number] | null) {
  // Pin the price axis to the observed bucket range (with 10% headroom
  // and floor). Without this, a wide fitted-band overlay (large σ) drags
  // the auto-fit range up to include values that dwarf the actual sales,
  // squishing the interesting part of the chart into a thin sliver.
  const priceAxis: Record<string, unknown> = {
    type: 'value',
    name: t('item.chart.price'),
    nameTextStyle: labelStyle,
    axisLine: { lineStyle: { color: border } },
    axisLabel: { ...labelStyle, formatter: (v: number) => `${v.toLocaleString()}g` },
    splitLine: { lineStyle: { color: border, opacity: 0.3 } },
  }
  if (priceRange) {
    const [lo, hi] = priceRange
    const pad = Math.max((hi - lo) * 0.1, 1)
    priceAxis.min = Math.max(0, Math.floor(lo - pad))
    priceAxis.max = Math.ceil(hi + pad)
  }
  return [
    priceAxis,
    {
      type: 'value',
      name: t('item.chart.units'),
      nameTextStyle: labelStyle,
      axisLine: { lineStyle: { color: border } },
      axisLabel: labelStyle,
    },
  ]
}

function minMaxSeries(bands: (string | number)[][], info: string) {
  return {
    name: t('item.chart.minMax'),
    type: 'custom' as const,
    renderItem: (
        _p: unknown,
        api: {
          value: (idx: number) => number
          coord: (v: [string, number]) => [number, number]
          style: () => Record<string, string>
        },
    ) => {
      const day = api.value(0)
      const bottom = api.coord([day as unknown as string, api.value(1)])
      const top = api.coord([day as unknown as string, api.value(2)])
      return {
        type: 'line',
        shape: { x1: bottom[0], y1: bottom[1], x2: top[0], y2: top[1] },
        style: { ...api.style(), stroke: info, lineWidth: 4, opacity: 0.35 },
      }
    },
    encode: { x: 0, y: [1, 2] },
    data: bands,
  }
}

function fittedBandSeries(band: [number, number] | null, warning: string) {
  if (!band) return []
  return [
    {
      name: t('item.chart.fittedBand'),
      type: 'line' as const,
      data: [] as [string, number][],
      lineStyle: { width: 0 },
      itemStyle: { color: warning },
      markLine: {
        symbol: 'none',
        lineStyle: { color: warning, opacity: 0.7, type: 'dashed' as const },
        label: { show: false },
        data: [{ yAxis: band[0] }, { yAxis: band[1] }],
      },
      markArea: {
        itemStyle: { color: warning, opacity: 0.08 },
        data: [[{ yAxis: band[0] }, { yAxis: band[1] }]],
      },
    },
  ]
}

function render() {
  if (!chart) return
  const colors = themeColors()
  const { text, border, info, success, warning, fontFamily } = colors as ThemeColors
  const labelStyle = { color: text, fontSize: 14, fontFamily, fontWeight: 500 }
  const days = props.buckets.map((b) => b.day)
  const avg = props.buckets.map((b) => b.avgPrice)
  const bands = props.buckets.map((b) => [b.day, b.minPrice, b.maxPrice])
  const units = props.buckets.map((b) => [b.day, b.units])
  const band = modelBand(props.model)
  // Axis range = observed bucket range only. A wide fitted band will be
  // clipped rather than allowed to squash the chart. If the model is
  // clearly narrower than the observed spread we still show it in full.
  const priceRange: [number, number] | null = props.buckets.length
    ? (() => {
        const lo = Math.min(...props.buckets.map((b) => b.minPrice))
        const hi = Math.max(...props.buckets.map((b) => b.maxPrice))
        if (!band) return [lo, hi]
        // Only widen for a band that already fits inside the bucket range,
        // so the tuning info doesn't get cropped when it agrees with data.
        return [Math.min(lo, band[0] >= lo ? band[0] : lo), Math.max(hi, band[1] <= hi ? band[1] : hi)]
      })()
    : null
  const legend: string[] = [t('item.chart.avgPrice'), t('item.chart.minMax'), t('item.chart.unitsSold')]
  if (band) legend.push(t('item.chart.fittedBand'))
  chart.setOption({
    textStyle: labelStyle,
    tooltip: { trigger: 'axis', textStyle: { fontFamily, fontSize: 14 } },
    legend: { data: legend, top: 4, textStyle: labelStyle },
    grid: { left: 70, right: 60, top: 40, bottom: 34 },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: border } },
      axisLabel: labelStyle,
      splitLine: { lineStyle: { color: border, opacity: 0.35 } },
    },
    yAxis: buildAxes(labelStyle, border, priceRange),
    series: [
      minMaxSeries(bands, info),
      {
        name: t('item.chart.avgPrice'),
        type: 'line',
        data: days.map((d, i) => [d, avg[i]]),
        smooth: true,
        showSymbol: true,
        lineStyle: { width: 3.5, color: success },
        itemStyle: { color: success },
      },
      {
        name: t('item.chart.unitsSold'),
        type: 'bar',
        yAxisIndex: 1,
        data: units,
        itemStyle: { color: info, opacity: 0.6 },
        barWidth: 12,
      },
      ...fittedBandSeries(band, warning),
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

watch(() => props.buckets, render, { deep: true })
watch(() => props.model, render, { deep: true })
watch(locale, render)
</script>

<template>
  <div ref="chartEl" class="h-72 w-full" />
</template>
