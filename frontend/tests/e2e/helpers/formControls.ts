import type { Page } from '@playwright/test';

const MONTH_ABBR = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * Drives the MonthField popover (src/components/ui/MonthField.tsx) — every start/end/issued/
 * expiry/date field across the profile forms uses it instead of a plain text input. `label`
 * must match the field's visible label exactly (e.g. "Start", "End", "Issued", "Date (optional)").
 */
export async function selectMonth(page: Page, label: string, value: string): Promise<void> {
  const [yearStr, monthStr] = value.split('-');
  const targetYear = Number(yearStr);
  const targetMonth = Number(monthStr) - 1; // 0-indexed to match MONTH_ABBR

  const trigger = page.getByRole('button', { name: label, exact: true });
  await trigger.click();

  const escapedLabel = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const dialog = page.getByRole('dialog', { name: new RegExp(`^${escapedLabel} —`) });
  // The grid always opens on the currently-selected month's year, or this year if empty —
  // step year-by-year to the target rather than assuming a fixed starting point.
  for (let guard = 0; guard < 200; guard += 1) {
    const yearText = await dialog.locator('span').first().innerText();
    const shownYear = Number(yearText);
    if (shownYear === targetYear) break;
    await dialog.getByRole('button', { name: shownYear < targetYear ? 'Next year' : 'Previous year' }).click();
  }

  await dialog.getByRole('button', { name: MONTH_ABBR[targetMonth], exact: true }).click();
}
