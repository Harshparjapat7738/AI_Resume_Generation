/**
 * Formatting/labelling helpers shared by the Dashboard summary and every dedicated data page
 * (Applications/Resumes/Cover Letters/Emails) — pulled out of DashboardPage.tsx so the two
 * never drift into slightly different labels/date formats for the same underlying fields.
 */
import type { ApplicationStatus, GenerationType } from '@/services/applicationApi';

export type TypeFilter = 'ALL' | GenerationType;
export type StatusFilter = 'ALL' | ApplicationStatus;

export const TYPE_FILTERS: { id: TypeFilter; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'RESUME_ONLY', label: 'Resume' },
  { id: 'COVER_LETTER_ONLY', label: 'Cover Letter' },
  { id: 'EMAIL_ONLY', label: 'Email' },
];

export const STATUS_FILTERS: { id: StatusFilter; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'COMPLETED', label: 'Completed' },
  { id: 'PROCESSING', label: 'Processing' },
  { id: 'FAILED', label: 'Failed' },
];

export function generationTypeLabel(type: GenerationType): string {
  switch (type) {
    case 'RESUME_ONLY':
      return 'Resume';
    case 'COVER_LETTER_ONLY':
      return 'Cover Letter';
    case 'EMAIL_ONLY':
      return 'Email';
    case 'ALL':
      return 'Resume + Cover Letter + Email';
    default:
      return type;
  }
}

export function templateLabel(templateId: string | null): string | null {
  if (!templateId) return null;
  return templateId
    .split('-')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function statusStyle(status: ApplicationStatus): string {
  switch (status) {
    case 'COMPLETED':
      return 'bg-mint/10 text-mint';
    case 'FAILED':
      return 'bg-ember/10 text-ember-soft';
    case 'PROCESSING':
      return 'bg-surface-2 text-ink-muted';
    default:
      return 'bg-surface-2 text-ink-faint';
  }
}
