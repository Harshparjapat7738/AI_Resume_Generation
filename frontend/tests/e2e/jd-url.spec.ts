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
  await page.getByRole('button', { name: 'Save & continue' }).click();
  await expect(page.getByRole('button', { name: 'Continue', exact: true })).toBeVisible();
  // 7 forward clicks: Personal(0)->Education->Experience->Skills->Projects->
  // Certifications->Achievements->Review(7).
  for (let i = 0; i < 7; i++) {
    await page.getByRole('button', { name: /Continue|Skip/ }).first().click();
  }
  await page.getByRole('button', { name: 'Finish profile' }).click();
  await page.waitForURL('/');

  await page.getByRole('link', { name: 'Generate' }).first().click();
  await page.waitForURL('**/generate');
  await page.getByRole('button', { name: /Resume/ }).click();
  await page.waitForURL('**/generate/job');
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

test('a successfully fetched URL reaches Review with the extracted text, and survives refresh', async ({ page }) => {
  await loginFreshUserAtJobDescriptionStep(page);

  // example.com is stable, always reachable, and has no JobPosting JSON-LD — this
  // exercises the generic text-extraction fallback path end-to-end through the real UI.
  await page.fill('#url', 'https://example.com');
  await page.getByRole('button', { name: 'Fetch job description' }).click();

  await page.waitForURL('**/generate/review/**', { timeout: 20_000 });
  await expect(page.getByText('Fetched from URL')).toBeVisible();
  await expect(page.getByText('https://example.com')).toBeVisible();
  await expect(page.getByText('Extracted description')).toBeVisible();
  await expect(page.getByText('Example Domain')).toBeVisible(); // example.com's own heading
  await expect(page.getByText('JDBREAKMARKER')).toHaveCount(0); // internal marker must never leak

  await page.reload();
  await expect(page.getByText('Fetched from URL')).toBeVisible();
  await expect(page.getByText('Example Domain')).toBeVisible();
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

  await page.waitForURL('**/generate/review/**', { timeout: 10_000 });
  await expect(page.getByText('Submitted text')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Confirm this is correct' })).toBeVisible();
});
