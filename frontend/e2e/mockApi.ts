/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { Page, Route } from '@playwright/test'
import type { MeResponse } from '../src/api/auth'
import type { RegionDto } from '../src/api/worlds'
import type { FilterRow } from '../src/api/filter'
import type { OffersResponse, ScoredOffer } from '../src/api/offers'
import type { CalibrationHistoryPoint, CapabilityStats } from '../src/api/dashboard'
import type { CalibrationSnapshot, KeyCalibration } from '../src/api/calibration'
import type { TrendBoard, WorldActivity } from '../src/api/trends'
import type { ItemDetailDto, SalesBucket } from '../src/api/items'
import type { AlertRule } from '../src/api/alerts'
import type { SkillLimits, SkillMap } from '../src/api/skills'

/** Instant every spec freezes the browser clock at; all fixture dates sit just before it. */
export const NOW = new Date('2026-07-01T12:00:00Z')

const hoursAgo = (h: number) => new Date(NOW.getTime() - h * 3_600_000).toISOString()

const me: MeResponse = { id: '1', username: 'tester', displayName: 'Tester', avatarUrl: null, locale: 'en' }

const worlds: RegionDto[] = [
  {
    name: 'Europe',
    dataCenters: [
      { id: 7, name: 'Light', worlds: [{ id: 66, name: 'Odin' }, { id: 402, name: 'Alpha' }, { id: 33, name: 'Twintania' }] },
    ],
  },
]

const filter: FilterRow = {
  worldId: 66,
  offerLimit: 1000,
  unitPrice: 1000,
  factor: 2,
  refreshHours: 6,
  popularity: 0,
  marketVolume: 0,
  interest: 0,
  sales: 0,
  views: 0,
  profit: 100,
  effectiveProfit: 10000,
  target: 'DATA_CENTER',
  budget: 250_000,
  inventorySlots: 0,
}

function offer(itemId: number, name: string, world: number, buy: number, net: number, conf: ScoredOffer['confidence']): ScoredOffer {
  const qty = 5
  return {
    sourceWorldId: world,
    itemId,
    itemName: name,
    hq: itemId % 2 === 0,
    quantity: qty,
    buyPrice: buy,
    depthAhead: itemId % 3,
    valuation: {
      expectedNet: net,
      sigmaNet: Math.round(net * 0.12),
      evGross: (net - buy) * qty,
      expectedTimeOnShelfHours: 2 + (itemId % 5),
      evPerHour: Math.round(((net - buy) * qty) / (1 + (itemId % 5))),
      listRatio: 0.95,
    },
    lambdaUndercut: 0,
    ghostFraction: 0,
    modelSufficient: true,
    modelPooled: conf === 'MEDIUM',
    confidence: conf,
  }
}

const offers: OffersResponse = {
  homeWorld: 'Odin',
  dataCenter: 'Light',
  count: 6,
  offers: [
    offer(5057, 'Darksteel Ingot', 402, 1200, 2900, 'HIGH'),
    offer(5058, 'Mythrite Ingot', 33, 800, 1700, 'HIGH'),
    offer(4850, 'Hi-Ether', 402, 400, 950, 'MEDIUM'),
    offer(6600, 'Rose Gold Nugget', 33, 2100, 3600, 'LOW'),
    offer(7001, 'Ironwood Lumber', 402, 650, 1100, 'MEDIUM'),
    offer(7602, 'Vanya Silk', 33, 3000, 4400, 'HIGH'),
  ],
}

const calibration: CalibrationSnapshot = {
  windowDays: 14,
  sampleCount: 1840,
  logRatioMean: -0.03,
  logRatioSigma: 0.21,
  keyMedianLogRatio: -0.02,
  interpretation: 'calibrated',
}

const history: CalibrationHistoryPoint[] = Array.from({ length: 14 }, (_, i) => ({
  capturedAt: hoursAgo((14 - i) * 24),
  sampleCount: 1500 + i * 25,
  logRatioMean: -0.05 + i * 0.002,
  logRatioSigma: 0.24 - i * 0.002,
}))

const stats: CapabilityStats = {
  totalModels: 52_000,
  sufficientModels: 31_400,
  uniqueItems: 11_200,
  uniqueWorlds: 8,
  totalListings: 940_000,
  totalSales: 3_100_000,
  enabledAlerts: 3,
  alertsFired24h: 1,
  lastRefitAt: hoursAgo(1),
  databaseBytes: 7_400_000_000,
  modelRefitMs: Array.from({ length: 12 }, (_, i) => ({ capturedAt: hoursAgo(12 - i), valueMs: 900 + i * 15, detail: null })),
  viewRefreshMs: Array.from({ length: 12 }, (_, i) => ({ capturedAt: hoursAgo(12 - i), valueMs: 300 + i * 5, detail: null })),
}

const worstKeys: KeyCalibration[] = [
  { itemId: 5057, itemName: 'Darksteel Ingot', worldId: 66, worldName: 'Odin', hq: false, count: 18, logRatioMean: -0.31, logRatioSigma: 0.2, bias: -0.31 },
  { itemId: 4850, itemName: 'Hi-Ether', worldId: 66, worldName: 'Odin', hq: true, count: 12, logRatioMean: 0.24, logRatioSigma: 0.15, bias: 0.24 },
]

const trends: TrendBoard = {
  homeWorldId: 66,
  windowDays: 7,
  fittedKeys: 420,
  trending: [
    { itemId: 5057, itemName: 'Darksteel Ingot', hq: false, slope: 4.2, relativeSlope: 0.31, r2: 0.72, weakFit: false, totalUnits: 96, avgUnitsPerDay: 13.7, lastDayUnits: 22, predictedNext24h: 25 },
  ],
  losing: [
    { itemId: 7602, itemName: 'Vanya Silk', hq: false, slope: -2.1, relativeSlope: -0.18, r2: 0.41, weakFit: false, totalUnits: 80, avgUnitsPerDay: 11.4, lastDayUnits: 6, predictedNext24h: 4 },
  ],
}

const activity: WorldActivity = {
  worldId: 66,
  totalUnits: 1312,
  totalGil: 4_820_000,
  hours: Array.from({ length: 24 }, (_, i) => ({
    hourStart: hoursAgo(23 - i),
    units: 20 + ((i * 37) % 60),
    gil: (20 + ((i * 37) % 60)) * 3600,
  })),
}

const item: ItemDetailDto = {
  itemId: 5057,
  itemName: 'Darksteel Ingot',
  category: 'Metal',
  description: 'An ingot of darksteel, used in high-end armor.',
  hq: false,
  homeWorldId: 66,
  homeWorldName: 'Odin',
  dataCenter: 'Light',
  model: {
    expectedPrice: 3050,
    medianPrice: 2980,
    sigma: 0.18,
    lambdaAggressive: 1.4,
    lambdaMedian: 0.9,
    lambdaAbove: 0.4,
    lambdaUndercut: 0,
    ghostFraction: 0,
    sampleCount: 64,
    sufficient: true,
    pooled: false,
    fittedAt: hoursAgo(2),
  },
  listings: [
    { worldId: 402, worldName: 'Alpha', unitPrice: 1200, quantity: 5, hq: false, reviewedAt: hoursAgo(1) },
    { worldId: 66, worldName: 'Odin', unitPrice: 2990, quantity: 12, hq: false, reviewedAt: hoursAgo(1) },
    { worldId: 33, worldName: 'Twintania', unitPrice: 1450, quantity: 3, hq: false, reviewedAt: hoursAgo(2) },
  ],
  desynth: null,
  crafts: [
    {
      recipeId: 1,
      craftClass: 'blacksmith',
      level: 50,
      yield: 1,
      ingredients: [
        { itemId: 5114, itemName: 'Darksteel Ore', quantity: 4, cheapestBuy: 220, craftPerUnit: null, chosenSource: 'buy', subIngredients: null, subRecipeClass: null, subRecipeLevel: null, depthCapped: false },
        { itemId: 5, itemName: 'Fire Shard', quantity: 2, cheapestBuy: 30, craftPerUnit: null, chosenSource: 'buy', subIngredients: null, subRecipeClass: null, subRecipeLevel: null, depthCapped: false },
      ],
      valuation: { expectedNet: 2900, sigmaNet: 300, evGross: 1960, expectedTimeOnShelfHours: 3, evPerHour: 650, listRatio: 0.95 },
    },
  ],
}

const salesHistory: SalesBucket[] = Array.from({ length: 7 }, (_, i) => ({
  day: hoursAgo((7 - i) * 24).slice(0, 10),
  sales: 6 + i,
  units: 12 + i * 2,
  avgPrice: 2900 + i * 20,
  minPrice: 2700,
  maxPrice: 3300,
}))

const alerts: AlertRule[] = [
  { id: 'a1', itemId: 5057, worldId: 66, dataCenterId: null, hq: null, kind: 'price_below', threshold: 1500, enabled: true, cooldownMinutes: 60, lastTriggeredAt: hoursAgo(5), createdAt: hoursAgo(200) },
  { id: 'a2', itemId: 4850, worldId: null, dataCenterId: 7, hq: true, kind: 'sale_volume_spike', threshold: 200, enabled: true, cooldownMinutes: 120, lastTriggeredAt: null, createdAt: hoursAgo(100) },
  { id: 'a3', itemId: 7602, worldId: 66, dataCenterId: null, hq: null, kind: 'listing_count_drop', threshold: 1, enabled: false, cooldownMinutes: 60, lastTriggeredAt: null, createdAt: hoursAgo(50) },
]

const skills: SkillMap = { craft: { blacksmith: 90 }, desynth: {} }
const skillLimits: SkillLimits = { craftMax: 100, desynthMax: 720 }

/** GET handlers keyed by API path below `/api/v1`. */
const routes: Record<string, unknown> = {
  '/me': me,
  '/worlds': worlds,
  '/me/filter': filter,
  '/offers': offers,
  '/calibration': calibration,
  '/calibration/history': history,
  '/calibration/keys': worstKeys,
  '/dashboard/stats': stats,
  '/trends': trends,
  '/worlds/66/sales/24h': activity,
  '/items/5057': item,
  '/items/5057/sales-history': salesHistory,
  '/alerts': alerts,
  '/item-names': { '5057': 'Darksteel Ingot', '4850': 'Hi-Ether', '7602': 'Vanya Silk' },
  '/me/skills': skills,
  '/skills/limits': skillLimits,
  '/me/planner-params': null,
  '/me/planner-presets': [],
  '/item-catalog': {},
}

/** Paths that answer 204, the API's "nothing to report". */
const noContent = new Set(['/items/5057/trend'])

/** Item icons answer 404 so every icon renders its fixed fallback glyph. */
const missingIcon = /^\/item-icon\/\d+$/

/**
 * Serve every API call from the fixtures above and block everything that
 * would leave the machine (CDN icons, fonts from elsewhere), so screenshots
 * depend on nothing but the SPA. Unknown API paths fail the test. With
 * `signedIn` false, `/me` answers 401 so the SPA shows the signed-out flow.
 */
export async function mockApi(page: Page, unknown: string[], signedIn = true): Promise<void> {
  await page.routeWebSocket(/\/api\/v1\/ws\//, () => {})
  await page.route(/^https?:\/\/(?!127\.0\.0\.1)/, (route) => route.abort())
  await page.route(/\/(api\/v1|auth)\//, (route: Route) => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace(/^\/api\/v1/, '')
    if (!signedIn && path === '/me') return route.fulfill({ status: 401 })
    if (noContent.has(path)) return route.fulfill({ status: 204 })
    if (missingIcon.test(path)) return route.fulfill({ status: 404 })
    if (route.request().method() === 'GET' && path in routes) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(routes[path]) })
    }
    unknown.push(`${route.request().method()} ${url.pathname}`)
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"not mocked"}' })
  })
}
