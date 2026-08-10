import { test, expect } from '@playwright/test';

/**
 * End-to-end coverage for COVER_LETTER_ONLY generation (application-service + ai-service;
 * ARCHITECTURE_DECISIONS.md ADR-020). Runs against the real backend (see
 * playwright.config.ts) — no mocking, so "grounded" here means what it means everywhere else
 * in this suite: the letter genuinely reflects the profile and job entered, not a
 * fabrication.
 *
 * Does not touch the RESUME_ONLY or EMAIL_ONLY flows — see resume-pdf.spec.ts,
 * template-selection.spec.ts and email-generation.spec.ts for their own continuous coverage.
 */

async function loginFreshUserToCoverLetterReview(
  page: import('@playwright/test').Page,
  emailPrefix: string,
  jobDescriptionText: string,
) {
  const email = `${emailPrefix}+${Date.now()}+${Math.random().toString(36).slice(2)}@example.com`;
  const password = 'correcthorsebattery';

  await page.goto('/register');
  await page.fill('#displayName', 'Cover Letter Tester');
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');

  await page.fill('#fullName', 'Morgan Lee');
  await page.getByRole('button', { name: 'Save & continue' }).click();
  // "Save & continue" only saves — the wizard's own nav button (relabelled "Continue" once
  // the section has data) is what actually advances the step.
  await expect(page.getByRole('button', { name: 'Continue', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Continue', exact: true }).click(); // personal -> education

  // Education (skipped) -> Experience.
  await page.getByRole('button', { name: /Continue|Skip/ }).first().click();

  await page.fill('#company', 'Initech Systems');
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
  await page.getByRole('button', { name: /Cover Letter/ }).click();
  await page.waitForURL('**/generate/job**');
  await expect(page.url()).toContain('type=COVER_LETTER_ONLY');

  await page.fill('#jobDescriptionText', jobDescriptionText);
  await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.waitForURL('**/generate/review/**');

  await page.getByRole('button', { name: 'Confirm this is correct' }).click();
  await expect(page.getByRole('button', { name: 'Generate my cover letter' })).toBeVisible({ timeout: 60_000 });
}

test('cover letter generation: correct job/company, grounded content, no fabricated facts, and persistence across a reload', async ({ page }) => {
  await loginFreshUserToCoverLetterReview(
    page,
    'e2e-cl-happy',
    'Senior Backend Engineer at Globex Inc. We need 3+ years of Java and Spring Boot '
      + 'experience building backend services.',
  );

  await page.getByRole('button', { name: 'Generate my cover letter' }).click();
  await page.waitForURL('**/results/cover-letter/**', { timeout: 60_000 });

  const letterCard = page.getByText('Cover Letter', { exact: true }).locator('..');

  // Correct job: the letter names the actual role and company applied to — never a
  // placeholder, never a different job.
  await expect(letterCard).toContainText('Backend Engineer');
  await expect(letterCard).toContainText('Globex Inc');

  // Grounded: draws on evidence actually entered (the real employer/technology).
  await expect(letterCard).toContainText('Initech Systems');
  await expect(letterCard).toContainText(/Java|Spring Boot/);
  await expect(letterCard).toContainText('Dear Hiring Manager');
  await expect(letterCard).toContainText('Sincerely');

  // No fabricated facts: a technology/employer never entered must never appear.
  await expect(letterCard).not.toContainText('Kubernetes');
  await expect(letterCard).not.toContainText('Umbrella Corp');

  // Persistence + refresh: a reload re-fetches from application-service (GET
  // /api/applications/{id}/cover-letter) rather than relying on transient navigation state.
  const textBefore = await letterCard.textContent();
  await page.reload();
  await expect(letterCard).toHaveText(textBefore ?? '');
  await expect(page.getByRole('button', { name: 'Regenerate' })).toBeVisible();
});

test('cover letter actions: copy and download are real, working buttons', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await loginFreshUserToCoverLetterReview(
    page,
    'e2e-cl-actions',
    'Backend Engineer at Wayne Enterprises. Java and Spring Boot experience required.',
  );
  await page.getByRole('button', { name: 'Generate my cover letter' }).click();
  await page.waitForURL('**/results/cover-letter/**', { timeout: 60_000 });

  await page.getByRole('button', { name: 'Copy', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Copied' })).toBeVisible();

  const clipboardText = await page.evaluate(() => navigator.clipboard.readText());
  expect(clipboardText).toContain('Sincerely');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/\.txt$/);
});

test('regenerating a cover letter produces a new version without creating a second application', async ({ page }) => {
  await loginFreshUserToCoverLetterReview(
    page,
    'e2e-cl-regenerate',
    'Backend Engineer at Stark Industries. Java and Spring Boot experience required.',
  );
  await page.getByRole('button', { name: 'Generate my cover letter' }).click();
  await page.waitForURL('**/results/cover-letter/**', { timeout: 60_000 });
  const applicationUrl = page.url();

  await page.getByRole('button', { name: 'Regenerate' }).click();
  await expect(page.getByText(/version 2/)).toBeVisible({ timeout: 30_000 });

  // Same application, same URL — regeneration is scoped to the existing application, not a
  // new, unrelated record.
  expect(page.url()).toBe(applicationUrl);
});

test('the resume flow is unaffected: Output Type still offers Resume as its own, separate path', async ({ page }) => {
  const email = `e2e-cl-noregress+${Date.now()}@example.com`;
  await page.goto('/register');
  await page.fill('#displayName', 'Regression Tester');
  await page.fill('#email', email);
  await page.fill('#password', 'correcthorsebattery');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');
  await page.fill('#fullName', 'Regression Tester');
  await page.getByRole('button', { name: 'Save & continue' }).click();

  await page.goto('/generate');
  await expect(page.getByRole('button', { name: /^Resume/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /Cover Letter/ })).toBeVisible();
  await page.getByRole('button', { name: /^Resume/ }).click();
  await page.waitForURL('**/generate/job**');
  expect(page.url()).toContain('type=RESUME_ONLY');
});
