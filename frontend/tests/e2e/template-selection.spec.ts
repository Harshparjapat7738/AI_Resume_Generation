import { test, expect } from '@playwright/test';
import { selectMonth } from './helpers/formControls';

/**
 * End-to-end coverage for the built-in template catalogue (resume-service's `templates`
 * collection; ARCHITECTURE_DECISIONS.md ADR-004, ADR-016) — listing, preview, selection,
 * persistence across the generation it's attached to, and the honest "Coming Soon" states
 * for upload/online. Runs against the real backend (see playwright.config.ts) — no mocking.
 */

async function loginFreshUserAtConfirmedReview(page: import('@playwright/test').Page, emailPrefix: string) {
  const email = `${emailPrefix}+${Date.now()}+${Math.random().toString(36).slice(2)}@example.com`;
  const password = 'correcthorsebattery';

  await page.goto('/register');
  await page.fill('#displayName', 'Template Tester');
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');

  // Minimal profile — enough evidence for generation to have something to work with.
  await page.fill('#fullName', 'Template Tester');
  // "Save & continue" both saves and advances (PersonalInfoForm's afterSave callback) —
  // there is no separate "Continue" button on this step.
  await page.getByRole('button', { name: 'Save & continue' }).click();
  await expect(page.getByText('Education')).toBeVisible();

  // Education (skipped — not required for generation) -> Experience.
  await page.getByRole('button', { name: /Continue|Skip/ }).first().click();

  await page.fill('#company', 'Acme Corp');
  await page.fill('#title', 'Backend Engineer');
  await selectMonth(page, 'Start', '2021-03');
  await selectMonth(page, 'End', '2024-01');
  await page.fill('#bullets', 'Built order-processing services\nReduced latency by 60%');
  await page.fill('#technologies', 'Java, Spring Boot');
  await page.getByRole('button', { name: 'Add experience' }).click();
  await expect(page.getByText('EXP-001')).toBeVisible();
  await page.getByRole('button', { name: 'Save & continue' }).click();

  // Skills, Projects, Certifications, Achievements, Review — skip the rest.
  for (let i = 0; i < 4; i++) {
    await page.getByRole('button', { name: /Continue|Skip/ }).first().click();
  }
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
}

test('template listing and preview: all three built-in templates render with a preview, name and description', async ({ page }) => {
  await loginFreshUserAtConfirmedReview(page, 'e2e-template-list');

  await page.getByRole('button', { name: 'Choose a template' }).click();
  await page.waitForURL('**/generate/template/**');

  for (const name of ['Classic', 'Modern ATS', 'Professional']) {
    const card = page.getByRole('button', { name: new RegExp(`^${name}`) });
    await expect(card).toBeVisible();
    await expect(card.getByRole('img')).toBeVisible(); // the honest structural preview mockup
    await expect(card.getByText('ATS-safe')).toBeVisible();
  }

  // Continue is disabled until a template is picked.
  await expect(page.getByRole('button', { name: 'Generate my resume' })).toBeDisabled();
});

test('selecting a template shows a clear selected state and enables Continue', async ({ page }) => {
  await loginFreshUserAtConfirmedReview(page, 'e2e-template-select');
  await page.getByRole('button', { name: 'Choose a template' }).click();
  await page.waitForURL('**/generate/template/**');

  const modernCard = page.getByRole('button', { name: /^Modern ATS/ });
  await modernCard.click();

  await expect(modernCard).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('button', { name: 'Generate my resume' })).toBeEnabled();
});

test('upload and online template sections are honestly disabled, never fake actions', async ({ page }) => {
  await loginFreshUserAtConfirmedReview(page, 'e2e-template-comingsoon');
  await page.getByRole('button', { name: 'Choose a template' }).click();
  await page.waitForURL('**/generate/template/**');

  await expect(page.getByText('Upload your own template')).toBeVisible();
  await expect(page.getByText('Browse online templates')).toBeVisible();
  await expect(page.getByText('🔒 Coming Soon')).toHaveCount(2);
});

test('direct navigation to processing without a selected template redirects back to template selection', async ({ page }) => {
  await loginFreshUserAtConfirmedReview(page, 'e2e-template-guard');
  await page.getByRole('button', { name: 'Choose a template' }).click();
  await page.waitForURL('**/generate/template/**');

  const jdId = page.url().split('/generate/template/')[1];
  await page.goto(`/generate/processing/${jdId}`);

  await page.waitForURL('**/generate/template/**');
  await expect(page.getByText('Choose a template', { exact: false }).first()).toBeVisible();
});

test('a selected template is used by generation and persists to the result', async ({ page }) => {
  await loginFreshUserAtConfirmedReview(page, 'e2e-template-persist');
  await page.getByRole('button', { name: 'Choose a template' }).click();
  await page.waitForURL('**/generate/template/**');

  await page.getByRole('button', { name: /^Professional/ }).click();
  await page.getByRole('button', { name: 'Generate my resume' }).click();
  await page.waitForURL('**/results/**', { timeout: 120_000 });

  await expect(page.getByText('Professional template')).toBeVisible();

  // Persists across a reload too — it's read back from the stored resume version, not
  // just held in transient navigation state.
  await page.reload();
  await expect(page.getByText('Professional template')).toBeVisible();
});
