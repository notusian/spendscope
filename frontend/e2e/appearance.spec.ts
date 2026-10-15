import { test, expect } from '@playwright/test'

test('theme selection persists and system mode follows browser preferences', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' })
  await page.goto('/')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.getByLabel('Appearance').selectOption('dark')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.reload()
  await expect(page.getByLabel('Appearance')).toHaveValue('dark')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.getByRole('link', { name: 'Log in', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.getByLabel('Appearance').selectOption('system')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
})

test('mobile navigation reaches settings and chart colours follow the selected theme', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.getByLabel('Appearance').selectOption('light')
  await page.getByRole('button', { name: 'Try the demo' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  const chart = page.locator('.recharts-line-curve').first()
  await expect(chart).toHaveAttribute('stroke', '#1434cb')
  await page.locator('.mobile-topbar').getByLabel('Appearance').selectOption('dark')
  await expect(chart).toHaveAttribute('stroke', '#9eb5ff')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.locator('.mobile-nav').getByRole('link', { name: 'Settings' }).click()
  await expect(page.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible()
  await page.locator('.mobile-nav').getByRole('link', { name: 'Security' }).click()
  await expect(page.getByRole('heading', { name: 'Security activity' })).toBeVisible()
  await page.locator('.mobile-topbar').getByRole('button', { name: 'Log out' }).click()
  await expect(page).toHaveURL(/\/login$/)
})

test('the landing page fits a narrow viewport in either theme', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 740 })
  await page.goto('/')
  for (const theme of ['light', 'dark']) {
    await page.getByLabel('Appearance').selectOption(theme)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  }
})
