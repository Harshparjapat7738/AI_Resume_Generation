import { test, expect } from '@playwright/test';
import { selectMonth } from './helpers/formControls';

/**
 * End-to-end coverage for JD optimization (jd-service + ai-service; ADR-033) — the product's
 * primary output now that resume and cover-letter generation have been removed. Runs against
 * the real backend (see playwright.config.ts) — no mocking, so "grounded" here means what it
 * means everywhere else in this suite: the optimization genuinely reflects the profile entered.
 *
 * The load-bearing assertion is the gap one: a requirement the profile cannot evidence must be
 * reported as missing rather than quietly matched. That is the whole promise of the product.
 */

const JOB_DESCRIPTION =
  'Senior Backend Engineer at Globex Inc. Required: 3+ years of Java, Spring Boot, and '
  + 'REST API design. Required: production experience with Apache Kafka event streaming. '
  + 'Preferred: Kubernetes and Terraform.';

async function registerProfileAndReachOptimizeStep(
  page: import('@playwright/test').Page,
  emailPrefix: string,
) {
  const email = `${emailPrefix}+${Date.now()}+${Math.random().toString(36).slice(2)}@example.com`;
  const password = 'correcthorsebattery';

  await page.goto('/register');
  await page.fill('#displayName', 'Optimization Tester');
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');

  await page.fill('#fullName', 'Jordan Rivera');
  await page.getByRole('button', { name: 'Save & continue' }).click();
  await expect(page.getByText('Education')).toBeVisible();

  // Education (skipped) -> Experience. Java/Spring Boot are evidenced; Kafka deliberately is
  // not, so the JD's Kafka requirement has to come back as a gap.
  await page.getByRole('button', { name: /Continue|Skip/ }).first().click();

  await page.fill('#company', 'Acme Corp');
  await page.fill('#title', 'Backend Engineer');
  await selectMonth(page, 'Start', '2021-03');
  await selectMonth(page, 'End', '2024-01');
  await page.fill('#bullets', 'Built order-processing services\nDesigned REST APIs for partners');
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
  // /generate now redirects straight into the JD step — output type is chosen later, after
  // skill gaps, not up front any more.
  await page.waitForURL('**/generate/job**');

  await page.fill('#jobDescriptionText', JOB_DESCRIPTION);
  await page.getByRole('button', { name: 'Continue', exact: true }).click();

  // The skill-gap step runs the real JD analysis + optimization automatically (one or two Groq
  // calls) — generous timeout, same as the other generation specs.
  await page.waitForURL('**/generate/skill-gap/**', { timeout: 10_000 });
  await expect(page.getByRole('button', { name: 'Continue' })).toBeVisible({ timeout: 120_000 });
  await page.getByRole('button', { name: 'Continue' }).click();

  await page.waitForURL('**/generate/output/**');
  await page.getByRole('button', { name: /JD Optimized Resume/ }).click();

  // The optimization already exists from the skill-gap step, so Processing here is fast — it
  // just re-reads the cached result (refresh=false) rather than spending another AI call.
  await page.waitForURL('**/results/optimization/**', { timeout: 30_000 });
  await expect(page.getByRole('heading', { name: 'Your JD Optimization Is Ready' }))
    .toBeVisible({ timeout: 30_000 });
}

test('JD optimization: keywords, matches and gaps derived from the real profile', async ({ page }) => {
  await registerProfileAndReachOptimizeStep(page, 'e2e-opt-happy');

  // Target role comes from the JD analysis, not from anything the user typed into a form.
  await expect(page.getByText(/Globex/i).first()).toBeVisible();

  // Every section the result page promises is present.
  await expect(page.getByRole('heading', { name: /Required skills/i })).toBeVisible();
  await expect(page.getByRole('heading', { name: /Preferred skills/i })).toBeVisible();
  await expect(page.getByRole('heading', { name: /Strong matches/i })).toBeVisible();
  await expect(page.getByRole('heading', { name: /Partial matches/i })).toBeVisible();
  await expect(page.getByRole('heading', { name: /Missing requirements/i })).toBeVisible();
  await expect(page.getByRole('heading', { name: /Keyword → evidence mapping/i })).toBeVisible();

  // Grounding, the part that matters: the profile evidences Java/Spring Boot, so at least one
  // JD keyword must map to the experience actually entered.
  await expect(page.getByText(/Backend Engineer.*Acme Corp|Acme Corp/).first()).toBeVisible();

  // …and the page must never invite claiming what the profile cannot support.
  await expect(page.getByText(/Not found in your verified profile/i)).toBeVisible();
});

test('optimization result survives a reload and is not regenerated', async ({ page }) => {
  await registerProfileAndReachOptimizeStep(page, 'e2e-opt-reload');

  const url = page.url();
  await page.reload();

  // Read back from `GET /api/jd/{id}/optimization` — the persisted result, no second AI call.
  await expect(page.getByRole('heading', { name: 'Your JD Optimization Is Ready' }))
    .toBeVisible({ timeout: 30_000 });
  expect(page.url()).toBe(url);
});

test('export actions are real: copy JSON, download JSON, and the external AI prompt', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await registerProfileAndReachOptimizeStep(page, 'e2e-opt-export');

  // Copy JSON — real clipboard content, parseable, with the schema's own shape.
  await page.getByRole('button', { name: 'Copy Optimization JSON' }).click();
  await expect(page.getByText('JSON copied to clipboard.')).toBeVisible();
  const copied = await page.evaluate(() => navigator.clipboard.readText());
  const parsed = JSON.parse(copied);
  expect(parsed).toHaveProperty('keywords');
  expect(parsed).toHaveProperty('requirementMatches');
  expect(parsed).toHaveProperty('missingRequirements');

  // Download JSON — a real file the browser actually saves.
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download JSON' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe('careerforge-jd-optimization.json');

  // Copy AI Prompt — opens the modal, and the prompt carries the data plus the no-invention
  // rules. No secret may ride along.
  await page.getByRole('button', { name: 'Copy AI Prompt' }).click();
  await expect(page.getByRole('dialog', { name: 'External AI prompt' })).toBeVisible();
  await page.getByRole('button', { name: 'Copy', exact: true }).click();
  await expect(page.getByText('Prompt copied to clipboard.')).toBeVisible();

  const prompt = await page.evaluate(() => navigator.clipboard.readText());
  expect(prompt).toContain('Do not invent');
  expect(prompt).toContain('Do not claim missing requirements');
  expect(prompt).toContain('FULL JD OPTIMIZATION DATA');
  expect(prompt).not.toMatch(/gsk_|api[_-]?key|Bearer /i);
});

test('ChatGPT handoff: a saved template is selected (never re-uploaded) and carried into the prompt', async ({
  page,
  context,
}) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  const email = `e2e-opt-template+${Date.now()}+${Math.random().toString(36).slice(2)}@example.com`;
  const password = 'correcthorsebattery';

  // My Templates only needs a session, not a completed profile — save one before the profile
  // wizard even starts, exactly the "upload once, reuse forever" promise this feature makes.
  await page.goto('/register');
  await page.fill('#displayName', 'Optimization Tester');
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');

  await page.goto('/profile/templates');
  await page.getByRole('button', { name: 'Add Template' }).click();
  await page.setInputFiles('#template-file', {
    name: 'resume.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\n%%EOF'),
  });
  await page.getByLabel('Template name').fill('My Professional Resume');
  await page.getByRole('button', { name: 'Save template' }).click();
  await expect(page.getByText('My Professional Resume')).toBeVisible();

  await page.goto('/onboarding');
  await page.fill('#fullName', 'Jordan Rivera');
  await page.getByRole('button', { name: 'Save & continue' }).click();
  await expect(page.getByText('Education')).toBeVisible();
  await page.getByRole('button', { name: /Continue|Skip/ }).first().click();

  await page.fill('#company', 'Acme Corp');
  await page.fill('#title', 'Backend Engineer');
  await selectMonth(page, 'Start', '2021-03');
  await selectMonth(page, 'End', '2024-01');
  await page.fill('#bullets', 'Built order-processing services\nDesigned REST APIs for partners');
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
  await page.waitForURL('**/generate/job**');
  await page.fill('#jobDescriptionText', JOB_DESCRIPTION);
  await page.getByRole('button', { name: 'Continue', exact: true }).click();

  await page.waitForURL('**/generate/skill-gap/**', { timeout: 10_000 });
  await expect(page.getByRole('button', { name: 'Continue' })).toBeVisible({ timeout: 120_000 });
  await page.getByRole('button', { name: 'Continue' }).click();

  await page.waitForURL('**/generate/output/**');
  await page.getByRole('button', { name: /JD Optimized Resume/ }).click();
  await page.waitForURL('**/results/optimization/**', { timeout: 30_000 });
  await expect(page.getByRole('heading', { name: 'Your JD Optimization Is Ready' })).toBeVisible({ timeout: 30_000 });

  // The template the user saved earlier is pre-selected as their default — nothing to upload
  // here, no "Upload Template" action exists on this page at all.
  await expect(page.getByRole('heading', { name: 'Choose your template' })).toBeVisible();
  await expect(page.getByLabel('Saved templates')).toHaveValue(/.+/);

  // Selecting a template changes the primary action from "Copy AI Prompt" to "Create with
  // ChatGPT" — the literal handoff entry point this feature adds.
  await expect(page.getByRole('button', { name: 'Create with ChatGPT' })).toBeVisible();
  await page.getByRole('button', { name: 'Create with ChatGPT' }).click();

  await expect(page.getByRole('dialog', { name: 'External AI prompt' })).toBeVisible();
  await expect(page.getByText('resume.pdf')).toBeVisible();
  await expect(page.getByText('Ready')).toHaveCount(2); // "JD optimization: Ready" + "Generation prompt: Ready"

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download Template' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe('resume.pdf');

  await page.getByRole('button', { name: 'Copy', exact: true }).click();
  const prompt = await page.evaluate(() => navigator.clipboard.readText());
  expect(prompt).toContain('SELECTED TEMPLATE');
  expect(prompt).toContain('My Professional Resume');
});
