import { defineConfig, devices } from '@playwright/test';

/**
 * Needs the full backend stack running (see README "Local setup") — these are true
 * end-to-end tests against real services, not mocked. `npm run e2e`.
 */
export default defineConfig({
  testDir: './tests/e2e',
  // A single test's *own* steps already assume up to 120s just for one generation call (see
  // e.g. profile.spec.ts's final `waitForURL('**/results/**', { timeout: 120_000 })`) — a
  // 120s *whole-test* timeout left no room for that plus the onboarding wizard and a JD
  // analysis call ahead of it. 300s gives a full onboarding+generate journey real headroom.
  timeout: 300_000,
  expect: { timeout: 15_000 },
  fullyParallel: false, // each test registers a real account against the real backend
  // `fullyParallel: false` only serializes tests *within* a file — Playwright still runs
  // separate spec files in parallel workers by default. That's fine against a real CI
  // backend with headroom, but against a single local dev stack it produces exactly the
  // kind of flakiness these tests are meant to catch (concurrent browsers competing for
  // CPU cause React re-renders to lag mid-interaction, detaching inputs Playwright is
  // mid-fill on) rather than real signal. One worker keeps every run fully serial.
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
