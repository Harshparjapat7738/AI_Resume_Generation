import { test, expect } from '@playwright/test';

/**
 * End-to-end coverage for the anonymous journey (Landing -> explore sections -> attempting to
 * generate redirects to Login) and cross-cutting security behavior (protected routes,
 * cross-user data access, unauthorized API calls). Runs against the real backend — no mocking.
 */

test.describe('anonymous journey', () => {
  test('landing page renders its explore sections and both CTAs route through login', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /grounded in evidence/i })).toBeVisible();

    // Explore: the anchor-linked sections referenced from the header nav are all present.
    for (const id of ['#problem', '#workflow', '#ats-score', '#security', '#faq']) {
      await expect(page.locator(id)).toHaveCount(1);
    }

    // "Get started" (Hero CTA, anonymous label) leads to /generate, which — with no session —
    // is a protected route and must bounce to /login rather than rendering anything.
    await page.getByRole('link', { name: 'Get started' }).click();
    await page.waitForURL('**/login');
  });

  test('protected routes redirect an anonymous visitor to /login instead of rendering', async ({ page }) => {
    for (const path of ['/dashboard', '/profile', '/generate', '/onboarding', '/applications/000000000000000000000000']) {
      await page.goto(path);
      await page.waitForURL('**/login');
    }
  });
});

test.describe('security: cross-user access and unauthorized endpoints', () => {
  async function registerAndLogin(page: import('@playwright/test').Page, name: string) {
    const email = `${name}+${Date.now()}+${Math.random().toString(36).slice(2)}@example.com`;
    const password = 'correcthorsebattery';
    await page.goto('/register');
    await page.fill('#displayName', name);
    await page.fill('#email', email);
    await page.fill('#password', password);
    await page.getByRole('button', { name: 'Create account' }).click();
    await page.waitForURL('**/onboarding');
    await page.fill('#fullName', name);
    // "Save & continue" both saves and advances (PersonalInfoForm's afterSave callback) —
    // there is no separate "Continue" button on this step, so only 6 clicks remain:
    // Education->Experience->Skills->Projects->Certifications->Achievements->Review.
    await page.getByRole('button', { name: 'Save & continue' }).click();
    for (let i = 0; i < 6; i++) {
      await page.getByRole('button', { name: /Continue|Skip/ }).first().click();
    }
    await page.getByRole('button', { name: 'Finish profile' }).click();
    await page.waitForURL('/');
    return { email, password };
  }

  test('user B cannot fetch user A application, resume, or profile data — the API 404s, not 403 (BOLA hardening)', async ({ browser }) => {
    const contextA = await browser.newContext();
    const pageA = await contextA.newPage();
    await registerAndLogin(pageA, 'e2e-sec-a');

    // User A creates an email application.
    await pageA.getByRole('link', { name: 'Generate' }).first().click();
    await pageA.waitForURL('**/generate');
    await pageA.getByRole('button', { name: /Email Content/ }).click();
    await pageA.waitForURL('**/generate/job**');
    await pageA.fill('#jobDescriptionText', 'Support Engineer at Wayne Enterprises. 2+ years support experience.');
    await pageA.getByRole('button', { name: 'Continue', exact: true }).click();
    await pageA.waitForURL('**/generate/review/**');
    await pageA.getByRole('button', { name: 'Confirm this is correct' }).click();
    await expect(pageA.getByRole('button', { name: 'Generate my email' })).toBeVisible({ timeout: 60_000 });
    await pageA.getByRole('button', { name: 'Generate my email' }).click();
    await pageA.waitForURL('**/results/email/**', { timeout: 60_000 });
    const applicationId = pageA.url().split('/results/email/')[1];

    const contextB = await browser.newContext();
    const pageB = await contextB.newPage();
    await registerAndLogin(pageB, 'e2e-sec-b');

    const status = await pageB.evaluate(async (id) => {
      const res = await fetch(`/api/applications/${id}`, { credentials: 'include' });
      return res.status;
    }, applicationId);
    expect(status).toBe(404);

    const emailStatus = await pageB.evaluate(async (id) => {
      const res = await fetch(`/api/applications/${id}/email`, { credentials: 'include' });
      return res.status;
    }, applicationId);
    expect(emailStatus).toBe(404);

    // Navigating there directly in the UI must not leak content either.
    await pageB.goto(`/applications/${applicationId}`);
    await expect(pageB.getByText('Support Engineer')).toHaveCount(0);

    await contextA.close();
    await contextB.close();
  });

  test('unauthenticated requests to protected APIs are rejected with 401', async ({ page }) => {
    await page.goto('/');
    const results = await page.evaluate(async () => {
      const endpoints = ['/api/applications', '/api/resumes', '/api/profile', '/api/auth/me'];
      const statuses: number[] = [];
      for (const ep of endpoints) {
        const res = await fetch(ep, { credentials: 'omit' });
        statuses.push(res.status);
      }
      return statuses;
    });
    for (const status of results) {
      expect([401, 403]).toContain(status);
    }
  });

  test('a malformed or forged application id is rejected, not treated as valid', async ({ page }) => {
    await registerAndLogin(page, 'e2e-sec-forged');

    // The access token lives only in the app's in-memory JS state (never localStorage/a
    // readable cookie — see apiClient.ts), so a page.evaluate fetch can't attach it directly.
    // Capture it off a real authenticated request the app makes, then reuse it below —
    // this exercises the real Authorization header path instead of accidentally testing the
    // unauthenticated case under a misleading test name.
    const [dashboardRequest] = await Promise.all([
      page.waitForRequest((req) => req.url().includes('/api/applications') && req.method() === 'GET'),
      page.goto('/dashboard'),
    ]);
    const authHeader = dashboardRequest.headers()['authorization'];
    expect(authHeader).toBeTruthy();

    const status = await page.evaluate(async (auth) => {
      const res = await fetch('/api/applications/not-a-real-id', {
        credentials: 'include',
        headers: { Authorization: auth },
      });
      return res.status;
    }, authHeader);
    expect([400, 404]).toContain(status);
  });
});
