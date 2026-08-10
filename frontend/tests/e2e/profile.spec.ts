import { test, expect } from '@playwright/test';

/**
 * End-to-end coverage for the complete-profile feature: an 8-step onboarding wizard
 * (Personal, Education, Experience, Skills, Projects, Certifications, Achievements,
 * Review), the always-editable /profile page, and the two behaviors that regressed
 * silently during development of this feature and are worth pinning down explicitly:
 *
 * 1. Logging out from a *protected* page must land on the public landing page, not
 *    /login (see AppHeader.tsx's handleLogout — ProtectedRoute reacts to the session
 *    clearing before an SPA navigate('/') call can win the race).
 * 2. An existing user with a complete profile logging back in must land on the landing
 *    page, not be sent back through onboarding.
 *
 * Runs against the real backend (see playwright.config.ts) — no mocking. A generation at
 * the end confirms the enriched evidence inventory (education/skills/projects/
 * certifications/achievements, not just experience) still flows through the real
 * ai-service pipeline without regressing the resume-generation feature built earlier.
 */
test('complete profile: onboarding, persistence, editing, logout/login, generation', async ({ page }) => {
  const email = `e2e-profile+${Date.now()}@example.com`;
  const password = 'correcthorsebattery';

  await test.step('register', async () => {
    await page.goto('/register');
    await page.fill('#displayName', 'Ada Lovelace');
    await page.fill('#email', email);
    await page.fill('#password', password);
    await page.getByRole('button', { name: 'Create account' }).click();
    await page.waitForURL('**/onboarding');
  });

  await test.step('onboarding: personal', async () => {
    await page.fill('#fullName', 'Ada Lovelace');
    await page.fill('#headline', 'Platform Engineer');
    await page.fill('#email', email);
    await page.getByRole('button', { name: 'Save & continue' }).click();
    await expect(page.getByRole('button', { name: 'Continue', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Continue', exact: true }).click();
  });

  await test.step('onboarding: education', async () => {
    await page.fill('#institution', 'MIT');
    await page.fill('#degree', 'BSc');
    await page.fill('#field', 'Computer Science');
    await page.fill('#start', '2015-09');
    await page.fill('#end', '2019-06');
    await page.getByRole('button', { name: 'Add education' }).click();
    await expect(page.getByText('EDU-001')).toBeVisible();
    await page.getByRole('button', { name: 'Continue', exact: true }).click();
  });

  await test.step('onboarding: experience', async () => {
    await page.fill('#company', 'Northwind Logistics');
    await page.fill('#title', 'Backend Engineer');
    await page.fill('#start', '2021-03');
    await page.fill('#end', '2024-01');
    await page.fill('#bullets', 'Built order-processing services\nReduced latency by 60%');
    await page.fill('#technologies', 'Java, Kubernetes');
    await page.getByRole('button', { name: 'Add experience' }).click();
    await expect(page.getByText('EXP-001')).toBeVisible();
    await page.getByRole('button', { name: 'Continue', exact: true }).click();
  });

  await test.step('onboarding: skills', async () => {
    await page.fill('#name', 'Kubernetes');
    await page.fill('#category', 'DevOps');
    await page.getByRole('button', { name: 'Add skill' }).click();
    await expect(page.getByText('DevOps')).toBeVisible();
    await page.getByRole('button', { name: 'Continue', exact: true }).click();
  });

  await test.step('onboarding: projects', async () => {
    await page.fill('#name', 'OpenTracker');
    await page.fill('#description', 'Open-source issue tracker.');
    await page.getByRole('button', { name: 'Add project' }).click();
    await expect(page.getByText('PROJ-001')).toBeVisible();
    await page.getByRole('button', { name: 'Continue', exact: true }).click();
  });

  await test.step('onboarding: certifications', async () => {
    await page.fill('#name', 'AWS Certified Solutions Architect');
    await page.fill('#issuer', 'AWS');
    await page.getByRole('button', { name: 'Add certification' }).click();
    await expect(page.getByText('CERT-001')).toBeVisible();
    await page.getByRole('button', { name: 'Continue', exact: true }).click();
  });

  await test.step('onboarding: achievements', async () => {
    await page.fill('#title', 'Hackathon Winner 2022');
    await page.getByRole('button', { name: 'Add achievement' }).click();
    await expect(page.getByText('ACH-001')).toBeVisible();
    await page.getByRole('button', { name: 'Continue', exact: true }).click();
  });

  await test.step('onboarding: review shows 100% and finishing lands on landing, not /apply or /evidence', async () => {
    await expect(page.getByText('Profile completion: 100%')).toBeVisible();
    await page.getByRole('button', { name: 'Finish profile' }).click();
    await page.waitForURL('/');
    await expect(page).toHaveURL('/');
  });

  await test.step('profile page shows every section and survives a refresh', async () => {
    await page.getByRole('link', { name: 'Profile' }).first().click();
    await page.waitForURL('**/profile');

    for (const text of ['MIT', 'Northwind Logistics', 'OpenTracker', 'AWS Certified Solutions Architect', 'Hackathon Winner 2022']) {
      await expect(page.getByText(text).first()).toBeVisible();
    }

    await page.reload();
    for (const text of ['MIT', 'Northwind Logistics', 'EXP-001']) {
      await expect(page.getByText(text).first()).toBeVisible();
    }
  });

  await test.step('logging out from a protected page (/profile) lands on the landing page, not /login', async () => {
    await page.getByRole('button', { name: 'Log out' }).first().click();
    await page.waitForURL('/');
    await expect(page).toHaveURL('/');
  });

  await test.step('an existing, complete-profile user logging back in lands on the landing page, not onboarding', async () => {
    await page.goto('/login');
    await page.fill('#email', email);
    await page.fill('#password', password);
    await page.getByRole('button', { name: 'Log in' }).click();
    await page.waitForURL('/');
    await expect(page).toHaveURL('/');
  });

  await test.step('resume generation still works with the enriched evidence inventory', async () => {
    await page.getByRole('link', { name: 'Generate' }).first().click();
    await page.waitForURL('**/generate');
    await page.getByRole('button', { name: /Resume/ }).click();
    await page.waitForURL('**/generate/job');

    await page.fill(
      '#jobDescriptionText',
      'DevOps Engineer at Acme Cloud. Need strong Kubernetes and AWS experience, AWS ' +
        'certification preferred. CI/CD automation required. Computer Science degree preferred.',
    );
    await page.getByRole('button', { name: 'Continue', exact: true }).click();
    await page.waitForURL('**/generate/review/**');

    await page.getByRole('button', { name: 'Confirm this is correct' }).click();
    await expect(page.getByRole('button', { name: 'Choose a template' })).toBeVisible({ timeout: 60_000 });
    await page.getByRole('button', { name: 'Choose a template' }).click();
    await page.waitForURL('**/generate/template/**');

    await page.getByRole('button', { name: /^Classic/ }).click();
    await page.getByRole('button', { name: 'Generate my resume' }).click();
    await page.waitForURL('**/results/**', { timeout: 120_000 });

    await expect(page.getByText('Result')).toBeVisible();
  });
});
