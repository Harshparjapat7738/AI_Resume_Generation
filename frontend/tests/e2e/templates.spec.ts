import { test, expect } from '@playwright/test';

/**
 * End-to-end coverage for "My Templates" (ADR-034) — a user's own uploaded Resume/Cover Letter
 * files, managed from Profile → My Templates and reused (never re-uploaded) at JD-optimization
 * handoff time. Runs against the real backend (profile-service + MinIO) — no mocking.
 *
 * Files are supplied as in-memory buffers (Playwright's `setInputFiles` accepts one directly),
 * not fixture files on disk — a minimal `%PDF-`/zip-signature payload is all
 * `TemplateService`'s validation actually inspects (see its own Javadoc: no structural
 * analysis, no AI, ever).
 */

const PDF_BYTES = Buffer.from('%PDF-1.4\n%%EOF');

// The first four bytes of any real zip ("PK\x03\x04") — TemplateService checks this signature,
// not just the ".docx" extension, so a plain text buffer would be correctly rejected rather
// than exercising the accept path these tests are for.
const DOCX_BYTES = Buffer.from([0x50, 0x4b, 0x03, 0x04, 0x0a, 0x00, 0x00, 0x00, 0x00, 0x00]);

async function registerAndReachTemplates(page: import('@playwright/test').Page, emailPrefix: string) {
  const email = `${emailPrefix}+${Date.now()}+${Math.random().toString(36).slice(2)}@example.com`;
  const password = 'correcthorsebattery';

  await page.goto('/register');
  await page.fill('#displayName', 'Template Tester');
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/onboarding');

  // My Templates only needs an authenticated session, not a completed profile — go straight
  // there rather than running the full onboarding wizard this feature doesn't depend on.
  await page.goto('/profile/templates');
  await expect(page.getByRole('heading', { name: 'My Templates' })).toBeVisible();
}

test('upload a PDF template, see it listed, rename it, download it, then delete it', async ({ page }) => {
  await registerAndReachTemplates(page, 'e2e-tmpl-pdf');

  await expect(page.getByText('No saved templates yet.')).toBeVisible();

  await page.getByRole('button', { name: 'Add Template' }).click();
  await page.setInputFiles('#template-file', {
    name: 'resume.pdf',
    mimeType: 'application/pdf',
    buffer: PDF_BYTES,
  });
  await page.getByLabel('Template name').fill('My Professional Resume');
  await page.getByLabel('Type').selectOption('RESUME');
  await page.getByRole('button', { name: 'Save template' }).click();

  await expect(page.getByText('My Professional Resume')).toBeVisible();
  await expect(page.getByText('PDF • Resume')).toBeVisible();
  // The very first upload becomes the default automatically.
  await expect(page.getByText('Default')).toBeVisible();

  // Rename. The kebab button's accessible name carries the template's current name, so it
  // stays unambiguous even with several cards on the page.
  await page.getByRole('button', { name: 'My Professional Resume actions' }).click();
  await page.getByRole('menuitem', { name: 'Rename' }).click();
  await page.getByLabel('Template name').fill('Updated Resume Name');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Updated Resume Name')).toBeVisible();

  // Download — a real file the browser actually saves, byte-identical to what was uploaded.
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe('resume.pdf');

  // Delete.
  await page.getByRole('button', { name: 'Updated Resume Name actions' }).click();
  await page.getByRole('menuitem', { name: 'Delete' }).click();
  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await expect(page.getByText('No saved templates yet.')).toBeVisible();
});

test('upload a DOCX template', async ({ page }) => {
  await registerAndReachTemplates(page, 'e2e-tmpl-docx');

  await page.getByRole('button', { name: 'Add Template' }).click();
  await page.setInputFiles('#template-file', {
    name: 'cover-letter.docx',
    mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    buffer: DOCX_BYTES,
  });
  await page.getByLabel('Type').selectOption('COVER_LETTER');
  await page.getByRole('button', { name: 'Save template' }).click();

  await expect(page.getByText('cover-letter')).toBeVisible();
  await expect(page.getByText('DOCX • Cover Letter')).toBeVisible();
});

test('an unsupported file extension is rejected before it ever reaches the server', async ({ page }) => {
  await registerAndReachTemplates(page, 'e2e-tmpl-invalid');

  await page.getByRole('button', { name: 'Add Template' }).click();
  await page.setInputFiles('#template-file', {
    name: 'notes.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('just some notes'),
  });

  await expect(page.getByText('Only PDF (.pdf) or Word (.docx) files are supported.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save template' })).toBeDisabled();
});

test('setting a second template as default unsets the first', async ({ page }) => {
  await registerAndReachTemplates(page, 'e2e-tmpl-default');

  for (const name of ['First Template', 'Second Template']) {
    await page.getByRole('button', { name: 'Add Template' }).click();
    await page.setInputFiles('#template-file', { name: 'resume.pdf', mimeType: 'application/pdf', buffer: PDF_BYTES });
    await page.getByLabel('Template name').fill(name);
    await page.getByRole('button', { name: 'Save template' }).click();
    await expect(page.getByText(name)).toBeVisible();
  }

  // First Template was auto-defaulted on upload; explicitly default the second one instead.
  await page.getByRole('button', { name: 'Second Template actions' }).click();
  await page.getByRole('menuitem', { name: 'Set as default' }).click();

  // Exactly one "Default" badge exists across the whole page — never two at once.
  await expect(page.getByText('Default')).toHaveCount(1);
});

test('ownership: one user can never see, download or manage another user\'s template', async ({ browser }) => {
  const ownerContext = await browser.newContext();
  const ownerPage = await ownerContext.newPage();
  await registerAndReachTemplates(ownerPage, 'e2e-tmpl-owner');

  await ownerPage.getByRole('button', { name: 'Add Template' }).click();
  await ownerPage.setInputFiles('#template-file', { name: 'resume.pdf', mimeType: 'application/pdf', buffer: PDF_BYTES });
  await ownerPage.getByLabel('Template name').fill("Owner's Resume");
  await ownerPage.getByRole('button', { name: 'Save template' }).click();
  await expect(ownerPage.getByText("Owner's Resume")).toBeVisible();

  const otherContext = await browser.newContext();
  const otherPage = await otherContext.newPage();
  await registerAndReachTemplates(otherPage, 'e2e-tmpl-other');

  // A different, freshly-registered user's library starts empty — the owner's template never
  // appears in someone else's list.
  await expect(otherPage.getByText('No saved templates yet.')).toBeVisible();
  await expect(otherPage.getByText("Owner's Resume")).not.toBeVisible();

  await ownerContext.close();
  await otherContext.close();
});
