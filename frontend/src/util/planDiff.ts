/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { PlanStop } from '@/api/planner'

/** How one route stop moved between two plans. */
export interface StopChange {
  kind: 'dropped' | 'added' | 'changed'
  worldId: number
  worldName: string
  /** Expected profit on the new stop minus the old one (0 when absent). */
  evDelta: number
  /** Spend on the new stop minus the old one. */
  spendDelta: number
}

/** Result of comparing two plans: per-stop changes plus the objective delta. */
export interface ReplanDiff {
  changes: StopChange[]
  objectiveDelta: number
}

/** Expected profit of a stop: the sum of every line's valuation; material lines count as zero. */
export function stopEv(stop: PlanStop): number {
  return stop.buys.reduce((sum, b) => sum + (b.valuation?.evGross ?? 0), 0)
}

function contentKey(stop: PlanStop): string {
  return stop.buys
    .map((b) => `${b.uniqueKey}:${b.qty}`)
    .sort()
    .join('|')
}

/**
 * Per-stop changes from `previous` to `next`. Worlds in `completed` are
 * ignored: a replan drops them on purpose. A stop present in both counts
 * as changed when its lines or quantities differ.
 */
export function diffStops(previous: PlanStop[], next: PlanStop[], completed: Set<number>): StopChange[] {
  const before = new Map(previous.filter((s) => !completed.has(s.worldId)).map((s) => [s.worldId, s]))
  const after = new Map(next.map((s) => [s.worldId, s]))
  const changes: StopChange[] = []
  for (const [id, old] of before) {
    const now = after.get(id)
    if (!now) {
      changes.push({ kind: 'dropped', worldId: id, worldName: old.worldName, evDelta: -stopEv(old), spendDelta: -old.buyCost })
    } else if (contentKey(old) !== contentKey(now)) {
      changes.push({
        kind: 'changed',
        worldId: id,
        worldName: now.worldName,
        evDelta: stopEv(now) - stopEv(old),
        spendDelta: now.buyCost - old.buyCost,
      })
    }
  }
  for (const [id, now] of after) {
    if (!before.has(id)) {
      changes.push({ kind: 'added', worldId: id, worldName: now.worldName, evDelta: stopEv(now), spendDelta: now.buyCost })
    }
  }
  return changes
}
