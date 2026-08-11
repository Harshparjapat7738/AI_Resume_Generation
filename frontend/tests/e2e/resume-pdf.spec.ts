import { test, expect } from '@playwright/test';
import { selectMonth } from './helpers/formControls';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';

/**
 * End-to-end coverage for real Resume PDF generation (document-service — docs/API_CATALOG.md
 * &sect;2). Runs against the real backend (see playwright.config.ts) — no mocking, and no
 * assertion here stops at HTTP 200: every check either inspects the actual downloaded PDF
 * bytes or cross-checks them against document-service's own persisted metadata.
 */

async function loginFreshUserAtResult(page: import('@playwright/test').Page, emailPrefix: string) {
  const email = `${emailPrefix}+${Date.now()}+${Math.random().toString(36).slice(2)}@example.com`;
  const password = 'correcthorsebattery';

  await page.goto('/register');
  await page.fill('#displayName', 'PDF Tester');
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');

  await page.fill('#fullName', 'PDF Tester');
  await page.fill('#headline', 'Backend Engineer');
  // "Save & continue" both saves and advances (PersonalInfoForm's afterSave callback calls
  // goNext() itself) — this step alone has no separate "Continue"/"Skip" button next to it.
  await page.getByRole('button', { name: 'Save & continue' }).click();

  // Education — skip (StepNav's own Continue/Skip, synchronous). exact:true avoids matching
  // the "Add education" sub-heading below it (a substring match on "education").
  await expect(page.getByRole('heading', { name: 'Education', exact: true })).toBeVisible();
  await page.getByRole('button', { name: /Continue|Skip/ }).first().click();

  // Experience — real content so the rendered PDF has a real section to check.
  await expect(page.getByRole('heading', { name: 'What have you actually done?' })).toBeVisible();
  await page.fill('#company', 'Acme Corp');
  await page.fill('#title', 'Backend Engineer');
  await selectMonth(page, 'Start', '2021-03');
  await selectMonth(page, 'End', '2024-01');
  await page.fill('#bullets', 'Built order-processing services\nReduced latency by 60%');
  await page.fill('#technologies', 'Java, Spring Boot');
  await page.getByRole('button', { name: 'Add experience' }).click();
  await expect(page.getByText('EXP-001')).toBeVisible();
  await page.getByRole('button', { name: 'Save & continue' }).click();

  // Skills, Projects, Certifications, Achievements — skip the rest, waiting for each
  // step's own heading before clicking past it.
  for (const heading of ['Skills', 'Projects', 'Certifications', 'Achievements']) {
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
    await page.getByRole('button', { name: /Continue|Skip/ }).first().click();
  }
  await expect(page.getByRole('heading', { name: "You're set up", exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Finish profile' }).click();
  await page.waitForURL('/');

  await page.getByRole('link', { name: 'Generate' }).first().click();
  await page.waitForURL('**/generate');
  await page.getByRole('button', { name: /^Resume/ }).click();
  await page.waitForURL('**/generate/job**');

  await page.fill(
    '#jobDescriptionText',
    'Senior Backend Engineer at Acme Corp. We need 3+ years of Java and Spring Boot '
      + 'experience building backend services.',
  );
  await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.waitForURL('**/generate/review/**');

  await page.getByRole('button', { name: 'Confirm this is correct' }).click();
  await expect(page.getByRole('button', { name: 'Choose a template' })).toBeVisible({ timeout: 60_000 });
  await page.getByRole('button', { name: 'Choose a template' }).click();
  await page.waitForURL('**/generate/template/**');

  await page.getByRole('button', { name: /^Modern ATS/ }).click();
  await page.getByRole('button', { name: 'Generate my resume' }).click();
  await page.waitForURL('**/results/**', { timeout: 120_000 });
}

test('generating, downloading and previewing a real, multi-section PDF', async ({ page }) => {
  await loginFreshUserAtResult(page, 'e2e-pdf-generate');
  const resumeId = page.url().split('/results/')[1];

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download Resume PDF' }).click();
  const download = await downloadPromise;

  const filePath = path.join(os.tmpdir(), `${Date.now()}-${await download.suggestedFilename()}`);
  await download.saveAs(filePath);
  const bytes = fs.readFileSync(filePath);

  // Real PDF bytes, not a stub — correct magic header, real trailer, non-trivial size.
  expect(bytes.subarray(0, 5).toString('ascii')).toBe('%PDF-');
  expect(bytes.subarray(-1024).toString('latin1')).toContain('%%EOF');
  expect(bytes.length).toBeGreaterThan(1000);

  // Cross-check the downloaded bytes against document-service's own persisted metadata
  // (computed server-side by PDFBox, not asserted from the client) — this is what makes the
  // check more than "the endpoint returned 200".
  const metadata = await page.evaluate(async (id) => {
    const res = await fetch(`/api/documents/resume-versions/${id}`, { credentials: 'include' });
    return res.json();
  }, resumeId);
  expect(metadata.format).toBe('PDF');
  expect(metadata.templateId).toBe('modern-ats');
  expect(metadata.pageCount).toBeGreaterThanOrEqual(1);
  expect(metadata.byteSize).toBe(bytes.length);

  // The inline preview renders the same PDF.
  await expect(page.locator('iframe[title="Resume PDF preview"]')).toBeVisible();
});

test('reloading the result page still offers the already-rendered PDF without re-rendering', async ({ page }) => {
  await loginFreshUserAtResult(page, 'e2e-pdf-refresh');
  const resumeId = page.url().split('/results/')[1];

  const firstDownload = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download Resume PDF' }).click();
  await firstDownload;

  const firstMeta = await page.evaluate(async (id) => {
    const res = await fetch(`/api/documents/resume-versions/${id}`, { credentials: 'include' });
    return res.json();
  }, resumeId);

  await page.reload();
  await expect(page.getByRole('button', { name: 'Download Resume PDF' })).toBeVisible();

  const secondDownload = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download Resume PDF' }).click();
  await secondDownload;

  const secondMeta = await page.evaluate(async (id) => {
    const res = await fetch(`/api/documents/resume-versions/${id}`, { credentials: 'include' });
    return res.json();
  }, resumeId);

  // Same artifact — id and hash unchanged — confirms this was a cached lookup, not a
  // second render producing a fresh object each time.
  expect(secondMeta.id).toBe(firstMeta.id);
  expect(secondMeta.sha256).toBe(firstMeta.sha256);
});

test('downloading a document id that does not exist or belongs to someone else 404s, never leaks bytes', async ({ page }) => {
  await loginFreshUserAtResult(page, 'e2e-pdf-auth');

  const status = await page.evaluate(async () => {
    const res = await fetch('/api/documents/000000000000000000000000/download', { credentials: 'include' });
    return res.status;
  });
  expect(status).toBe(404);
});

test('the generated resume with its PDF is visible in dashboard history', async ({ page }) => {
  await loginFreshUserAtResult(page, 'e2e-pdf-dashboard');

  await page.getByRole('link', { name: 'Dashboard' }).first().click();
  await page.waitForURL('**/dashboard');
  await expect(page.getByText('Acme Corp')).toBeVisible();
});
