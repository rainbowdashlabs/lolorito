/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { expect, test } from '@playwright/test'
import { NOW, mockApi } from './mockApi'

const WIDTHS = [360, 768, 1280] as const
const THEMES = ['light', 'dark'] as const

const VIEWS = [
  { name: 'login', path: '/login', ready: 'main' },
  { name: 'dashboard', path: '/', ready: 'text=Darksteel Ingot' },
  { name: 'offers', path: '/offers', ready: 'table' },
  { name: 'item', path: '/item/5057', ready: 'text=Listings on' },
  { name: 'planner', path: '/planner', ready: 'main' },
  { name: 'alerts', path: '/alerts', ready: 'text=Vanya Silk' },
] as const

for (const view of VIEWS) {
  for (const theme of THEMES) {
    for (const width of WIDTHS) {
      test(`${view.name} ${theme} ${width}`, async ({ page }) => {
        const unknown: string[] = []
        await page.clock.setFixedTime(NOW)
        await page.addInitScript((t) => {
          window.localStorage.setItem('lolorito_theme_v1', t)
          window.localStorage.setItem('lolorito_locale_v1', 'en')
        }, theme)
        await mockApi(page, unknown)
        await page.setViewportSize({ width, height: 900 })

        await page.goto(view.path)
        await page.locator(view.ready).first().waitFor()
        await page.waitForLoadState('networkidle')
        await page.evaluate(() => document.fonts.ready)

        expect(unknown, 'every API call must be mocked').toEqual([])
        await expect(page).toHaveScreenshot(`${view.name}-${theme}-${width}.png`, {
          fullPage: true,
          mask: [page.locator('canvas')],
        })
      })
    }
  }
}
