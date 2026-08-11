import type { ProfileCompletion } from '@/services/profileApi';

/**
 * Single source of truth for the profile page's section order and labels — the section nav
 * and the single-section content area (ProfilePage.tsx) both read from this so they can
 * never drift out of sync with each other. Keys match `ProfileCompletion['sections']`
 * (services/profileApi.ts) exactly; nothing here changes what "complete" means.
 */
export const PROFILE_SECTIONS = [
  { key: 'personal', label: 'Personal' },
  { key: 'education', label: 'Education' },
  { key: 'experience', label: 'Experience' },
  { key: 'skills', label: 'Skills' },
  { key: 'projects', label: 'Projects' },
  { key: 'certifications', label: 'Certifications' },
  { key: 'achievements', label: 'Achievements' },
] as const satisfies ReadonlyArray<{ key: keyof ProfileCompletion['sections']; label: string }>;

export type ProfileSectionKey = (typeof PROFILE_SECTIONS)[number]['key'];
