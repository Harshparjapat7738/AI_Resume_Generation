import { test, expect } from '@playwright/test';
import { selectMonth } from './helpers/formControls';

/**
 * End-to-end coverage for the "Generate All" workflow (Resume + Cover Letter + Email sharing
 * one Application — application-service; ARCHITECTURE_DECISIONS.md ADR-022). Runs against the
 * real backend (see playwright.config.ts) — no mocking. Verifies the single-application
 * invariant, that all five result tabs (Resume/Cover Letter/Email/ATS/JD Fit) are reachable
 * from the one canonical `/applications/:id` (a.k.a. `/results/all/:id`) URL, and that the
 * result — including downloads — survives a reload.
 */

async function loginFreshUserToGenerateAllReview(
  page: import('@playwright/test').Page,
  emailPrefix: string,
  jobDescriptionText: string,
) {
  const email = `${emailPrefix}+${Date.now()}+${Math.random().toString(36).slice(2)}@example.com`;
  const password = 'correcthorsebattery';

  await page.goto('/register');
  await page.fill('#displayName', 'Generate All Tester');
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');

  await page.fill('#fullName', 'Taylor Reed');
  // "Save & continue" both saves and advances (PersonalInfoForm's afterSave callback) —
  // there is no separate "Continue" button on this step.
  await page.getByRole('button', { name: 'Save & continue' }).click();
  await expect(page.getByText('Education')).toBeVisible();
  await page.getByRole('button', { name: /Continue|Skip/ }).first().click(); // education -> experience

  await page.fill('#company', 'Hooli');
  await page.fill('#title', 'Backend Engineer');
  await selectMonth(page, 'Start', '2021-03');
  await selectMonth(page, 'End', '2024-01');
  await page.fill('#bullets', 'Built order-processing services\nReduced latency by 60%');
  await page.fill('#technologies', 'Java, Spring Boot');
  await page.getByRole('button', { name: 'Add experience' }).click();
  await expect(page.getByText('EXP-001')).toBeVisible();
  await page.getByRole('button', { name: 'Save & continue' }).click();

  for (let i = 0; i < 4; i++) {
    await page.getByRole('button', { name: /Continue|Skip/ }).first().click();
  }
  await page.getByRole('button', { name: 'Finish profile' }).click();
  await page.waitForURL('/');

  await page.getByRole('link', { name: 'Generate' }).first().click();
  await page.waitForURL('**/generate');
  await page.getByRole('button', { name: /Generate All/ }).click();
  await page.waitForURL('**/generate/job**');
  await expect(page.url()).toContain('type=ALL');

  await page.fill('#jobDescriptionText', jobDescriptionText);
  await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.waitForURL('**/generate/review/**');

  await page.getByRole('button', { name: 'Confirm this is correct' }).click();
  await expect(page.getByRole('button', { name: 'Choose a template' })).toBeVisible({ timeout: 60_000 });
  await page.getByRole('button', { name: 'Choose a template' }).click();
  await page.waitForURL('**/generate/template/**');
}

test('Generate All: one application, all three outputs, every result tab populated, and it survives a reload', async ({ page }) => {
  await loginFreshUserToGenerateAllReview(
    page,
    'e2e-genall-happy',
    'Senior Backend Engineer at Hooli. We need 3+ years of Java and Spring Boot experience '
      + 'building backend services.',
  );

  await page.getByRole('button', { name: /^Modern ATS/ }).click();
  await page.getByRole('button', { name: 'Generate everything' }).click();

  // Processing -> a single canonical result URL, not three separate application records.
  await page.waitForURL(/\/results\/all\/[a-f0-9]+/, { timeout: 180_000 });
  const applicationUrl = page.url();

  // Status tracker shows all three outputs done.
  await expect(page.getByText('Resume', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Cover Letter', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Email', { exact: true }).first()).toBeVisible();

  // Resume tab (default) has content and a working PDF download.
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download Resume PDF' }).click();
  const download = await downloadPromise;
  expect(await download.suggestedFilename()).toMatch(/\.pdf$/);

  // Cover Letter tab.
  await page.getByRole('button', { name: 'Cover Letter' }).click();
  await expect(page.getByRole('button', { name: 'Download' })).toBeVisible();

  // Email tab.
  await page.getByRole('button', { name: 'Email', exact: true }).click();
  await expect(page.getByText('Subject')).toBeVisible();
  await expect(page.getByText('Body')).toBeVisible();

  // ATS Analysis tab — score + keywords + recommendations.
  await page.getByRole('button', { name: 'ATS Analysis' }).click();
  await expect(page.getByText('ATS Compatibility')).toBeVisible({ timeout: 30_000 });

  // JD Fit tab — score, keyword match, matched/missing keywords, recommendations.
  await page.getByRole('button', { name: 'JD Fit' }).click();
  await expect(page.getByText('Job Description Match')).toBeVisible();
  await expect(page.getByText('Keyword Match')).toBeVisible();

  // Reload: same URL, same application, all outputs still present (not re-generated, not lost).
  await page.reload();
  await expect(page).toHaveURL(applicationUrl);
  await expect(page.getByRole('button', { name: 'Download Resume PDF' })).toBeVisible();
});

test('Generate All application appears once (not three times) in the dashboard', async ({ page }) => {
  await loginFreshUserToGenerateAllReview(
    page,
    'e2e-genall-dashboard',
    'Backend Engineer at Massive Dynamic. Java and Spring Boot experience required.',
  );
  await page.getByRole('button', { name: /^Modern ATS/ }).click();
  await page.getByRole('button', { name: 'Generate everything' }).click();
  await page.waitForURL(/\/results\/all\/[a-f0-9]+/, { timeout: 180_000 });

  await page.getByRole('link', { name: 'Dashboard' }).first().click();
  await page.waitForURL('**/dashboard');

  await expect(page.getByText('Massive Dynamic')).toHaveCount(1);
  await expect(page.getByText('Resume + Cover Letter + Email')).toBeVisible();
});
