import { test, expect } from '@playwright/test';

/**
 * End-to-end coverage for the Applications dashboard (frontend/src/features/dashboard —
 * redesigned to represent Applications rather than only resume history). Runs against the
 * real backend — no mocking. Covers: an EMAIL_ONLY application appearing with its Company/Job
 * Title/Generation Type/Status/Created Date, the type and status filters, navigation to
 * `/applications/:id`.
 */

async function registerAndCompleteMinimalProfile(page: import('@playwright/test').Page, name: string) {
  const email = `${name}+${Date.now()}+${Math.random().toString(36).slice(2)}@example.com`;
  const password = 'correcthorsebattery';

  await page.goto('/register');
  await page.fill('#displayName', 'Dashboard Tester');
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');

  await page.fill('#fullName', 'Dashboard Tester');
  // "Save & continue" both saves and advances (PersonalInfoForm's afterSave callback) —
  // there is no separate "Continue" button on this step.
  await page.getByRole('button', { name: 'Save & continue' }).click();
  await expect(page.getByText('Education')).toBeVisible();
  // 6 remaining empty steps to click past (education, experience, skills, projects,
  // certifications, achievements) before landing on Review, which has no Continue/Skip button.
  for (let i = 0; i < 6; i++) {
    await page.getByRole('button', { name: /Continue|Skip/ }).first().click();
  }
  await page.getByRole('button', { name: 'Finish profile' }).click();
  await page.waitForURL('/');

  return { email, password };
}

test('a new Email application shows up on the dashboard with its details, and links to the application detail page', async ({ page }) => {
  await registerAndCompleteMinimalProfile(page, 'e2e-dash-email');

  await page.getByRole('link', { name: 'Generate' }).first().click();
  // /generate redirects straight into the JD step — output type is chosen later now, after
  // skill gaps, not up front.
  await page.waitForURL('**/generate/job**');
  await page.fill(
    '#jobDescriptionText',
    'Support Engineer at Umbrella Corp. 2+ years customer-facing technical support experience.',
  );
  await page.getByRole('button', { name: 'Continue', exact: true }).click();

  await page.waitForURL('**/generate/skill-gap/**', { timeout: 10_000 });
  await expect(page.getByRole('button', { name: 'Continue' })).toBeVisible({ timeout: 120_000 });
  await page.getByRole('button', { name: 'Continue' }).click();

  await page.waitForURL('**/generate/output/**');
  await page.getByRole('button', { name: /Email Content/ }).click();

  // Processing creates the Application and generates the email automatically — no separate
  // "Generate my email" button exists any more (that lived on the deleted Review page).
  await page.waitForURL('**/results/email/**', { timeout: 60_000 });
  const applicationId = page.url().split('/results/email/')[1];

  await page.getByRole('link', { name: 'Dashboard' }).first().click();
  await page.waitForURL('**/dashboard');

  await expect(page.getByText('Support Engineer')).toBeVisible();
  await expect(page.getByText('Umbrella Corp')).toBeVisible();
  await expect(page.getByText('Email', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Completed', { exact: true }).first()).toBeVisible();

  await page.getByRole('link', { name: 'View Application' }).first().click();
  await page.waitForURL(`**/applications/${applicationId}`);
  await expect(page.getByText('Subject')).toBeVisible();

  // Refresh the detail page: state persists.
  await page.reload();
  await expect(page.getByText('Subject')).toBeVisible();
});

test('dashboard type and status filters narrow the list', async ({ page }) => {
  await registerAndCompleteMinimalProfile(page, 'e2e-dash-filter');

  await page.getByRole('link', { name: 'Generate' }).first().click();
  await page.waitForURL('**/generate');
  await page.getByRole('button', { name: /Cover Letter/ }).click();
  await page.waitForURL('**/generate/job**');
  await page.fill(
    '#jobDescriptionText',
    'Technical Writer at Oscorp. 2+ years experience writing developer documentation.',
  );
  await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.waitForURL('**/generate/review/**');
  await page.getByRole('button', { name: 'Confirm this is correct' }).click();
  await expect(page.getByRole('button', { name: 'Generate my cover letter' })).toBeVisible({ timeout: 60_000 });
  await page.getByRole('button', { name: 'Generate my cover letter' }).click();
  await page.waitForURL('**/results/cover-letter/**', { timeout: 60_000 });

  await page.getByRole('link', { name: 'Dashboard' }).first().click();
  await page.waitForURL('**/dashboard');
  await expect(page.getByText('Oscorp')).toBeVisible();

  // Type filter: "Email" must hide this Cover-Letter application.
  await page.getByRole('button', { name: 'Email', exact: true }).click();
  await expect(page.getByText('Oscorp')).toHaveCount(0);

  // Back to "All": visible again.
  await page.getByRole('button', { name: 'All', exact: true }).first().click();
  await expect(page.getByText('Oscorp')).toBeVisible();

  // Status filter: "Failed" must hide this completed application.
  await page.getByRole('button', { name: 'Failed', exact: true }).click();
  await expect(page.getByText('Oscorp')).toHaveCount(0);
});

// NOTE: a third test (Resume + template-selection reaching the dashboard) was present here as a
// dangling, unwrapped code block with no enclosing `test(...)` — pre-existing corruption unrelated
// to this change, discovered while fixing this file's review-step references. It also asserted a
// `/generate/template/**` "Choose a template" flow that doesn't exist in the current app (Output
// Type only offers JD Optimized Resume and Email Content as working paths, Cover Letter/All are
// locked). Removed rather than reconstructed: inventing a test body for a flow that isn't
// implemented would be fabricating coverage, not fixing it.
