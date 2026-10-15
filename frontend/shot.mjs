import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium, expect } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173'
const output = path.resolve(process.env.SCREENSHOT_DIR ?? '../docs/screenshots')
await mkdir(output, { recursive: true })
const browser = await chromium.launch()
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1050 }, colorScheme: 'light' })
  await page.goto(baseURL)
  await page.getByLabel('Appearance').selectOption('light')
  await page.screenshot({ path: path.join(output, 'landing.png'), fullPage: true })
  await page.getByLabel('Appearance').selectOption('dark')
  await page.screenshot({ path: path.join(output, 'landing-dark.png'), fullPage: true })
  await page.getByLabel('Appearance').selectOption('light')
  await page.getByRole('button', { name: 'Try the demo' }).click()
  await page.waitForURL(/dashboard/)
  await expect(page.locator('.recharts-line-curve').first()).toBeVisible()
  await expect(page.getByText('Loading…')).toHaveCount(0)
  await page.screenshot({ path: path.join(output, 'dashboard.png'), fullPage: true })
  await page.locator('.sidebar').getByLabel('Appearance').selectOption('dark')
  await expect(page.locator('.recharts-line-curve').first()).toHaveAttribute('stroke', '#9eb5ff')
  await page.screenshot({ path: path.join(output, 'dashboard-dark.png'), fullPage: true })
  await page.locator('.sidebar').getByLabel('Appearance').selectOption('light')
  for (const route of ['transactions', 'budgets']) {
    await page.goto(`${baseURL}/${route}`)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(page.getByText('Loading…')).toHaveCount(0)
    await page.screenshot({ path: path.join(output, `${route}.png`), fullPage: true })
  }
  await page.setViewportSize({ width: 390, height: 844 })
  for (const route of ['dashboard', 'transactions']) {
    await page.goto(`${baseURL}/${route}`)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(page.getByText('Loading…')).toHaveCount(0)
    await page.screenshot({ path: path.join(output, `mobile-${route}.png`), fullPage: true })
  }
  console.log(`Screenshots saved to ${output}`)
} finally {
  await browser.close()
}
