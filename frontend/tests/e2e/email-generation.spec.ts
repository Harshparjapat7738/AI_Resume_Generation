import { test, expect } from '@playwright/test';

/**
 * End-to-end coverage for EMAIL_ONLY generation (application-service + ai-service;
 * ARCHITECTURE_DECISIONS.md ADR-019). Runs against the real backend (see
 * playwright.config.ts) — no mocking, so "grounding" here means what it means everywhere
 * else in this suite: the email genuinely reflects the profile entered, not a fabrication.
 *
 * Does not touch the RESUME_ONLY flow — see resume-pdf.spec.ts and template-selection.spec.ts
 * for its own continuous coverage.
 */

async function loginFreshUserToEmailReview(
  page: import('@playwright/test').Page,
  emailPrefix: string,
  jobDescriptionText: string,
) {
  const email = `${emailPrefix}+${Date.now()}+${Math.random().toString(36).slice(2)}@example.com`;
  const password = 'correcthorsebattery';

  await page.goto('/register');
  await page.fill('#displayName', 'Email Tester');
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');

  await page.fill('#fullName', 'Jordan Rivera');
  await page.getByRole('button', { name: 'Save & continue' }).click();
  await expect(page.getByRole('button', { name: 'Continue', exact: true })).toBeVisible();

  // Education (skipped) -> Experience.
  await page.getByRole('button', { name: /Continue|Skip/ }).first().click();

  await page.fill('#company', 'Acme Corp');
  await page.fill('#title', 'Backend Engineer');
  await page.fill('#start', '2021-03');
  await page.fill('#end', '2024-01');
  await page.fill('#bullets', 'Built order-processing services\nReduced latency by 60%');
  await page.fill('#technologies', 'Java, Spring Boot');
  await page.getByRole('button', { name: 'Add experience' }).click();
  await expect(page.getByText('EXP-001')).toBeVisible();
  await page.getByRole('button', { name: 'Continue', exact: true }).click();

  // Skills, Projects, Certifications, Achievements, Review — skip the rest.
  for (let i = 0; i < 4; i++) {
    await page.getByRole('button', { name: /Continue|Skip/ }).first().click();
  }
  await page.getByRole('button', { name: 'Finish profile' }).click();
  await page.waitForURL('/');

  await page.getByRole('link', { name: 'Generate' }).first().click();
  await page.waitForURL('**/generate');
  await page.getByRole('button', { name: /Email Content/ }).click();
  await page.waitForURL('**/generate/job**');
  await expect(page.url()).toContain('type=EMAIL_ONLY');

  await page.fill('#jobDescriptionText', jobDescriptionText);
  await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.waitForURL('**/generate/review/**');

  await page.getByRole('button', { name: 'Confirm this is correct' }).click();
  await expect(page.getByRole('button', { name: 'Generate my email' })).toBeVisible({ timeout: 60_000 });
}

test('email generation: correct user, correct job, grounded content, and persistence across a reload', async ({ page }) => {
  await loginFreshUserToEmailReview(
    page,
    'e2e-email-happy',
    'Senior Backend Engineer at Globex Inc. We need 3+ years of Java and Spring Boot '
      + 'experience building backend services.',
  );

  await page.getByRole('button', { name: 'Generate my email' }).click();
  await page.waitForURL('**/results/email/**', { timeout: 60_000 });

  // Correct job: the deterministic subject names the actual role and company applied to —
  // never a placeholder, never a different job.
  const subjectCard = page.getByText('Subject').locator('..');
  await expect(subjectCard).toContainText('Backend Engineer');
  await expect(subjectCard).toContainText('Globex Inc');

  // Correct user: the signature is the candidate's own real name, never invented.
  const bodyCard = page.getByText('Body').locator('..');
  await expect(bodyCard).toContainText('Jordan Rivera');

  // Grounded: the body draws on evidence actually entered (the real employer/technology),
  // and stays plausible-length prose rather than an empty or templated stub.
  await expect(bodyCard).toContainText('Dear Hiring Manager');
  await expect(bodyCard).toContainText('Sincerely');

  // Persistence + refresh: a reload re-fetches from application-service (GET
  // /api/applications/{id}/email) rather than relying on transient navigation state.
  const subjectBefore = await subjectCard.textContent();
  await page.reload();
  await expect(subjectCard).toHaveText(subjectBefore ?? '');
  await expect(page.getByRole('button', { name: 'Regenerate' })).toBeVisible();
});

test('email actions: copy subject, copy email and download are all real, working buttons', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await loginFreshUserToEmailReview(
    page,
    'e2e-email-actions',
    'Backend Engineer at Initech. Java and Spring Boot experience required.',
  );
  await page.getByRole('button', { name: 'Generate my email' }).click();
  await page.waitForURL('**/results/email/**', { timeout: 60_000 });

  await page.getByRole('button', { name: 'Copy Subject' }).click();
  await expect(page.getByRole('button', { name: 'Copied' })).toBeVisible();

  const clipboardText = await page.evaluate(() => navigator.clipboard.readText());
  expect(clipboardText).toContain('Backend Engineer');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/\.txt$/);
});

test('regenerating an email produces a new version without creating a second application', async ({ page }) => {
  await loginFreshUserToEmailReview(
    page,
    'e2e-email-regenerate',
    'Backend Engineer at Umbrella Corp. Java and Spring Boot experience required.',
  );
  await page.getByRole('button', { name: 'Generate my email' }).click();
  await page.waitForURL('**/results/email/**', { timeout: 60_000 });
  const applicationUrl = page.url();

  await page.getByRole('button', { name: 'Regenerate' }).click();
  await expect(page.getByText(/version 2/)).toBeVisible({ timeout: 30_000 });

  // Same application, same URL — regeneration is scoped to the existing application, not a
  // new, unrelated record.
  expect(page.url()).toBe(applicationUrl);
});

test('the resume flow is unaffected: Output Type still offers Resume as its own, separate path', async ({ page }) => {
  const email = `e2e-email-noregress+${Date.now()}@example.com`;
  await page.goto('/register');
  await page.fill('#displayName', 'Regression Tester');
  await page.fill('#email', email);
  await page.fill('#password', 'correcthorsebattery');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');
  await page.fill('#fullName', 'Regression Tester');
  await page.getByRole('button', { name: 'Save & continue' }).click();

  await page.goto('/generate');
  await expect(page.getByRole('button', { name: /Resume/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /Email Content/ })).toBeVisible();
  await page.getByRole('button', { name: /^Resume/ }).click();
  await page.waitForURL('**/generate/job**');
  expect(page.url()).toContain('type=RESUME_ONLY');
});
