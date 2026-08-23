import { test, expect } from '@playwright/test';

/**
 * End-to-end coverage for job description URL extraction (jd-service's SSRF-guarded
 * `POST /api/jd/fetch-url`, ARCHITECTURE_DECISIONS.md ADR-015). Runs against the real
 * backend — no mocking. Registers a fresh, minimal-profile account per test (profile
 * completeness isn't relevant here) and drives straight to the JD input step.
 */

async function loginFreshUserAtJobDescriptionStep(page: import('@playwright/test').Page) {
  const email = `e2e-jdurl+${Date.now()}+${Math.random().toString(36).slice(2)}@example.com`;
  const password = 'correcthorsebattery';

  await page.goto('/register');
  await page.fill('#displayName', 'JD URL Tester');
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');

  // Minimal profile: just enough to reach the generate flow. Fill personal, skip the rest.
  await page.fill('#fullName', 'JD URL Tester');
  // "Save & continue" both saves and advances (PersonalInfoForm's afterSave callback) —
  // there is no separate "Continue" button on this step, so we land on Education already.
  await page.getByRole('button', { name: 'Save & continue' }).click();
  await expect(page.getByText('Education')).toBeVisible();
  // 6 forward clicks: Education->Experience->Skills->Projects->Certifications->
  // Achievements->Review.
  for (let i = 0; i < 6; i++) {
    await page.getByRole('button', { name: /Continue|Skip/ }).first().click();
  }
  await page.getByRole('button', { name: 'Finish profile' }).click();
  await page.waitForURL('/');

  await page.getByRole('link', { name: 'Generate' }).first().click();
  // /generate redirects straight into the JD step — output type is chosen later now, after
  // skill gaps, not up front.
  await page.waitForURL('**/generate/job**');
  await page.getByRole('button', { name: 'Job URL' }).click();
}

test('unsupported/private URLs are rejected with the paste fallback, never fetched', async ({ page }) => {
  await loginFreshUserAtJobDescriptionStep(page);

  await page.fill('#url', 'http://127.0.0.1/');
  await page.getByRole('button', { name: 'Fetch job description' }).click();

  await expect(page.getByText('Unable to extract this job description from this URL.')).toBeVisible();
  const fallback = page.getByRole('button', { name: 'Paste Job Description Instead' });
  await expect(fallback).toBeVisible();

  await fallback.click();
  await expect(page.getByLabel('Job description')).toBeVisible();
});

test('a URL that fetches but returns no usable content shows the same honest failure', async ({ page }) => {
  await loginFreshUserAtJobDescriptionStep(page);

  // A real, stable domain returning a real 404 — genuinely fetchable, genuinely fails.
  await page.fill('#url', 'https://example.com/this-page-does-not-exist-404');
  await page.getByRole('button', { name: 'Fetch job description' }).click();

  await expect(page.getByText('Unable to extract this job description from this URL.')).toBeVisible({
    timeout: 20_000,
  });
});

test('client-side validation catches an obviously malformed URL before any request', async ({ page }) => {
  await loginFreshUserAtJobDescriptionStep(page);

  await page.fill('#url', 'not a url');
  await page.getByRole('button', { name: 'Fetch job description' }).click();

  await expect(page.getByText('Enter a valid URL, including https://')).toBeVisible();
});

test('a successfully fetched URL is submitted and proceeds straight to skill gaps', async ({ page }) => {
  await loginFreshUserAtJobDescriptionStep(page);

  // example.com is stable, always reachable, and has no JobPosting JSON-LD — this exercises
  // the generic text-extraction fallback path end-to-end through the real UI. There is no
  // review step to preview the extracted text on any more — a successful fetch goes straight
  // to the skill-gap step, which analyses and optimizes the extracted text directly.
  await page.fill('#url', 'https://example.com');
  await page.getByRole('button', { name: 'Fetch job description' }).click();

  await page.waitForURL('**/generate/skill-gap/**', { timeout: 20_000 });
});

test('the existing paste workflow is unaffected', async ({ page }) => {
  await loginFreshUserAtJobDescriptionStep(page);

  await page.getByRole('button', { name: 'Paste Job Description' }).click();
  await page.fill(
    '#jobDescriptionText',
    'Senior Java Developer at Acme Corp. We are looking for a Senior Java Developer with '
      + '5+ years of experience building backend services with Java and Spring Boot.',
  );
  await page.getByRole('button', { name: 'Continue', exact: true }).click();

  // No review/confirm step any more — Continue goes straight to skill gaps.
  await page.waitForURL('**/generate/skill-gap/**', { timeout: 10_000 });
});
