/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { expect, test, type Page } from '@playwright/test'
import { NOW, mockApi } from './mockApi'

async function open(page: Page, width: number, theme = 'light') {
  await page.clock.setFixedTime(NOW)
  await page.addInitScript((t) => {
    window.localStorage.setItem('lolorito_theme_v1', t)
    window.localStorage.setItem('lolorito_locale_v1', 'en')
  }, theme)
  await mockApi(page, [])
  await page.setViewportSize({ width, height: 900 })
  await page.goto('/')
  await page.getByText('Darksteel Ingot').first().waitFor()
}

test('menu button opens the navigation and a link closes it', async ({ page }) => {
  await open(page, 360)
  const toggle = page.getByRole('button', { name: 'Open menu' })
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await expect(page.locator('#mobile-nav')).toHaveCount(0)

  await toggle.click()
  const menu = page.locator('#mobile-nav')
  await expect(menu.getByRole('link')).toHaveText([
    'Dashboard', 'Offers', 'Planner', 'Baskets', 'Alerts', 'Desynth', 'Shopping', 'Settings',
  ])
  await expect(page.getByRole('button', { name: 'Close menu' })).toHaveAttribute('aria-expanded', 'true')

  await menu.getByRole('link', { name: 'Alerts' }).click()
  await expect(page).toHaveURL(/\/alerts$/)
  await expect(page.locator('#mobile-nav')).toHaveCount(0)
})

test('escape closes the menu', async ({ page }) => {
  await open(page, 360)
  await page.getByRole('button', { name: 'Open menu' }).click()
  await expect(page.locator('#mobile-nav')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.locator('#mobile-nav')).toHaveCount(0)
})

test('desktop widths show the bar and no menu button', async ({ page }) => {
  await open(page, 1280)
  await expect(page.getByRole('button', { name: 'Open menu' })).toBeHidden()
  await expect(page.locator('header nav').first().getByRole('link', { name: 'Offers' })).toBeVisible()
})

for (const theme of ['light', 'dark']) {
  test(`open menu ${theme} 360`, async ({ page }) => {
    await open(page, 360, theme)
    await page.getByRole('button', { name: 'Open menu' }).click()
    await page.waitForLoadState('networkidle')
    await expect(page.locator('header')).toHaveScreenshot(`mobile-menu-${theme}-360.png`)
  })
}
