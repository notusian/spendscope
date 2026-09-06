import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end configuration.
 *
 * Playwright starts the Vite dev server, which proxies `/api` to a backend
 * expected to already be running on port 8080. Running the frontend through
 * the proxy rather than hitting the API directly means the test exercises the
 * same single-origin cookie and CSRF behaviour a real user gets.
 */
export default defineConfig({
  testDir: './e2e',
  // The suite writes to a shared database, so tests must not race each other.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI ? [['html', { open: 'never' }], ['list']] : 'list',

  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],

  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: 'npm run dev',
        url: 'http://localhost:5173',
        reuseExistingServer: !process.env.CI,
        timeout: 60_000,
      },
})
