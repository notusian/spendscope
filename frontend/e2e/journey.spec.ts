import { test, expect, type Page } from '@playwright/test'

/**
 * The scenario from the product spec, driven through a real browser.
 *
 * Open the site -> register -> create an account -> upload a CSV -> open the
 * dashboard -> verify the spending total and the category breakdown.
 *
 * Deliberately asserts on rendered numbers, not just on navigation. A dashboard
 * that loads but shows nothing is still broken.
 */

/** A fresh email per run, so repeated runs never collide on the unique index. */
function uniqueEmail(): string {
  return `e2e-${Date.now()}-${Math.floor(Math.random() * 10_000)}@example.com`
}

const PASSWORD = 'correct-horse-battery'

// Page headings are <h1> and card titles are <h3>. Locators for page headings
// pin level: 1 so that, for example, "Accounts" does not also match the
// "Your accounts" card title.

/** Rows dated the 1st so "this month" always contains them, on any run date. */
function statementCsv(): string {
  const now = new Date()
  const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  const previous = new Date(now.getFullYear(), now.getMonth() - 1, 1)
  const previousMonth = `${previous.getFullYear()}-${String(previous.getMonth() + 1).padStart(2, '0')}`

  return [
    'date,description,amount',
    `${previousMonth}-01,NTUC FAIRPRICE #171,-50.00`,
    `${previousMonth}-01,STARBUCKS COFFEE #4821,-10.00`,
    `${month}-01,NTUC FAIRPRICE #171,-120.00`,
    `${month}-01,STARBUCKS COFFEE #4821,-30.00`,
    `${month}-01,SPOTIFY P34A9B21,-12.98`,
    '',
  ].join('\n')
}

async function register(page: Page, email: string) {
  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

test.describe('SpendScope end-to-end', () => {
  test('register, import a statement and read the dashboard', async ({ page }) => {
    const email = uniqueEmail()

    // --- 1. Landing page -------------------------------------------------
    await page.goto('/')
    await expect(page.getByRole('heading', { name: /where your money actually goes/i })).toBeVisible()

    // --- 2. Register (which logs the user straight in) --------------------
    await register(page, email)
    await expect(page.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible()

    // --- 3. Create the "DBS Debit" account --------------------------------
    await page.getByRole('link', { name: 'Accounts' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Accounts' })).toBeVisible()

    await page.getByLabel('Name').fill('DBS Debit')
    await page.getByLabel('Type').selectOption('CHECKING')
    await page.getByRole('button', { name: 'Create account' }).click()

    await expect(page.getByRole('cell', { name: 'DBS Debit', exact: true })).toBeVisible()

    // --- 4. Upload the statement ------------------------------------------
    await page.getByRole('link', { name: 'Import' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Import transactions' })).toBeVisible()

    await page.getByLabel('Import into').selectOption({ label: 'DBS Debit' })
    await page.getByLabel('CSV file').setInputFiles({
      name: 'statement.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(statementCsv(), 'utf-8'),
    })
    await page.getByRole('button', { name: 'Import' }).click()

    // 5 rows in, 5 imported, none rejected.
    await expect(page.getByText(/Imported 5 of 5 rows/)).toBeVisible()

    // --- 5. Transactions were categorised ---------------------------------
    await page.getByRole('link', { name: 'Transactions' }).click()
    // exact: true so "Spotify" matches only the merchant cell, not the raw
    // description cell or the row's action buttons.
    await expect(page.getByRole('cell', { name: 'Spotify', exact: true })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'FairPrice', exact: true }).first()).toBeVisible()

    // --- 6. The dashboard shows the spending total ------------------------
    await page.getByRole('link', { name: 'Dashboard' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible()

    // This month: 120.00 + 30.00 + 12.98 = 162.98
    const totalCard = page.locator('.card', { hasText: 'TOTAL SPENDING' }).first()
    await expect(totalCard.locator('.metric-value')).toContainText('162.98')

    // --- 7. The category breakdown rendered with the right categories -----
    const categoriesCard = page.locator('.card', { hasText: 'CATEGORIES' }).first()
    await expect(categoriesCard).toBeVisible()
    // The chart surface proves Recharts drew; the legend text proves it drew
    // the right data. Asserting only that "an svg exists" would pass on an
    // empty chart.
    await expect(categoriesCard.locator('svg.recharts-surface').first()).toBeVisible()
    await expect(categoriesCard).toContainText('Groceries')
    await expect(categoriesCard).toContainText('Food')
    await expect(categoriesCard).toContainText('Subscriptions')

    // --- 8. The spending trend chart rendered -----------------------------
    const trendCard = page.locator('.card', { hasText: 'SPENDING OVER TIME' }).first()
    await expect(trendCard.locator('svg.recharts-surface').first()).toBeVisible()

    // --- 8b. Top merchants list is populated ------------------------------
    const merchantsCard = page.locator('.card', { hasText: 'TOP MERCHANTS' }).first()
    await expect(merchantsCard).toContainText('FairPrice')

    // --- 9. Log out and back in; the session works ------------------------
    await page.getByRole('button', { name: 'Log out' }).first().click()
    await expect(page).toHaveURL(/\/login$/)

    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill(PASSWORD)
    await page.getByRole('button', { name: 'Log in' }).click()

    await expect(page).toHaveURL(/\/dashboard$/)
    await expect(
      page.locator('.card', { hasText: 'TOTAL SPENDING' }).first().locator('.metric-value'),
    ).toContainText('162.98')
  })

  test('an unauthenticated visitor is sent to the login page', async ({ page }) => {
    // Frontend route protection is a convenience, not the security boundary -
    // but it should still work.
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('the demo flow fills an account with data', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: 'Try the demo' }).click()

    await expect(page).toHaveURL(/\/dashboard$/, { timeout: 30_000 })
    await expect(page.getByText('Demo account').first()).toBeVisible()

    // The demo must actually contain data, or it demonstrates nothing.
    const totalCard = page.locator('.card', { hasText: 'TOTAL SPENDING' }).first()
    await expect(totalCard.locator('.metric-value')).not.toHaveText('—')
  })
})
