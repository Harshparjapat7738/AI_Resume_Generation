import type { ProfileResponse } from '@/services/profileApi';

export interface KeywordEvidence {
  keyword: string;
  /** True if a Skill with this exact name (case-insensitive) already exists in the profile. */
  hasSkill: boolean;
  /** Experience/project evidence IDs whose `technologies` list already names this keyword —
   *  the same structured field resume generation and the assessment engine already read (the
   *  "Consider highlighting genuine experience with: …" recommendation text is sourced from
   *  exactly this field). Never derived from free-text bullets/descriptions — those aren't a
   *  structured signal, so treating a substring hit there as "evidence" would be a guess, not
   *  a fact. */
  evidenceIds: string[];
}

const normalize = (value: string): string => value.trim().toLowerCase();

/** Whether the candidate's profile already supports a given missing keyword, and if so, which
 *  experience/project entries carry it — used to decide whether the Missing Keywords panel
 *  offers "Add to Profile" (no evidence yet) or "Include in Resume" (evidence already exists,
 *  nothing new to create). Never invents or infers evidence; only reports what's already
 *  explicitly recorded. */
export function findKeywordEvidence(keyword: string, profile: ProfileResponse): KeywordEvidence {
  const target = normalize(keyword);
  const hasSkill = profile.skills.some((skill) => normalize(skill.name ?? '') === target);
  const evidenceIds: string[] = [];
  for (const experience of profile.experiences) {
    if (experience.technologies.some((tech) => normalize(tech) === target)) {
      evidenceIds.push(experience.evidenceId);
    }
  }
  for (const project of profile.projects) {
    if (project.technologies.some((tech) => normalize(tech) === target)) {
      evidenceIds.push(project.evidenceId);
    }
  }
  return { keyword, hasSkill, evidenceIds };
}

/** Adds `value` to `existing` only if it isn't already present (case-insensitive) — the
 *  dedup rule behind "Do not create duplicate profile skills" / duplicate technologies tags. */
export function addUnique(existing: string[], value: string): string[] {
  const target = normalize(value);
  if (existing.some((item) => normalize(item) === target)) return existing;
  return [...existing, value];
}
