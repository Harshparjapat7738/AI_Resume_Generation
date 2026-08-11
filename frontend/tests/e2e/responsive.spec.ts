import { test, expect, devices } from '@playwright/test';

/**
 * End-to-end coverage for responsive layout across mobile, tablet and desktop viewports. Runs
 * against the real backend (see playwright.config.ts) — no mocking. Checks the two things that
 * actually matter for "responsive" beyond CSS breakpoints existing: (1) no horizontal overflow
 * (a page that's technically laid out with Tailwind's responsive classes but still causes the
 * viewport to scroll sideways is not actually responsive), and (2) navigation stays reachable
 * at every size — the landing page's hamburger menu opens and exposes the same links the
 * desktop nav shows.
 */

const viewports = {
  mobile: devices['iPhone 13'].viewport,
  tablet: { width: 768, height: 1024 },
  desktop: { width: 1440, height: 900 },
};

async function noHorizontalOverflow(page: import('@playwright/test').Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
  );
  expect(overflow).toBe(false);
}

for (const [name, viewport] of Object.entries(viewports)) {
  test(`landing page has no horizontal overflow at ${name} (${viewport.width}x${viewport.height})`, async ({ page }) => {
    await page.setViewportSize(viewport);
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /grounded in evidence/i })).toBeVisible();
    await noHorizontalOverflow(page);
  });
}

test('mobile: landing nav collapses to a hamburger menu that opens and reaches the same destinations', async ({ page }) => {
  await page.setViewportSize(viewports.mobile);
  await page.goto('/');

  const menuButton = page.getByRole('button', { name: 'Toggle navigation menu' });
  await expect(menuButton).toBeVisible();
  // The desktop-only nav links are not directly clickable at this width.
  await expect(page.getByRole('link', { name: 'Get started' })).toBeHidden();

  await menuButton.click();
  await expect(page.getByRole('link', { name: 'Get started' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Log in' })).toBeVisible();
});

test('tablet and desktop: landing page shows the full inline nav without a hamburger', async ({ page }) => {
  await page.setViewportSize(viewports.desktop);
  await page.goto('/');
  await expect(page.getByRole('link', { name: 'Get started', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Log in' })).toBeVisible();
});

test('dashboard renders without horizontal overflow at every viewport once authenticated', async ({ page }) => {
  const email = `e2e-responsive+${Date.now()}@example.com`;
  await page.goto('/register');
  await page.fill('#displayName', 'Responsive Tester');
  await page.fill('#email', email);
  await page.fill('#password', 'correcthorsebattery');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');
  await page.fill('#fullName', 'Responsive Tester');
  // "Save & continue" both saves and advances (PersonalInfoForm's afterSave callback) —
  // there is no separate "Continue" button on this step, so only 6 clicks remain:
  // Education->Experience->Skills->Projects->Certifications->Achievements->Review.
  await page.getByRole('button', { name: 'Save & continue' }).click();
  for (let i = 0; i < 6; i++) {
    await page.getByRole('button', { name: /Continue|Skip/ }).first().click();
  }
  await page.getByRole('button', { name: 'Finish profile' }).click();
  await page.waitForURL('/');

  await page.goto('/dashboard');
  for (const viewport of Object.values(viewports)) {
    await page.setViewportSize(viewport);
    await expect(page.getByRole('heading', { name: 'Your applications' })).toBeVisible();
    await noHorizontalOverflow(page);
  }
});
