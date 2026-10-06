/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const gilFormatter = new Intl.NumberFormat('en-US')

export function formatGil(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '—'
  return gilFormatter.format(Math.round(value))
}

/** Human-readable duration for `hours` — "3h 12m", "2d 4h", "45m". */
export function formatHours(hours: number | null | undefined): string {
  if (hours == null || !Number.isFinite(hours) || hours < 0) return '—'
  if (hours < 1 / 60) return '<1m'
  if (hours < 1) return `${Math.round(hours * 60)}m`
  if (hours < 24) {
    const h = Math.floor(hours)
    const m = Math.round((hours - h) * 60)
    return m === 0 ? `${h}h` : `${h}h ${m}m`
  }
  const days = Math.floor(hours / 24)
  const remH = Math.round(hours - days * 24)
  return remH === 0 ? `${days}d` : `${days}d ${remH}h`
}

export function formatPercent(fraction: number, digits = 1): string {
  if (!Number.isFinite(fraction)) return '—'
  return `${(fraction * 100).toFixed(digits)}%`
}
