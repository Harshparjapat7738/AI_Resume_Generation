import type { Requirement } from '@/services/jdApi';
import type { ProfileResponse } from '@/services/profileApi';
import { findKeywordEvidence } from '@/features/results/utils/keywordEvidence';

export type AlignmentStatus = 'MATCHED' | 'PARTIAL' | 'MISSING';

export interface AlignmentItem {
  requirementId: string;
  text: string;
  type: string;
  status: AlignmentStatus;
  /** The normalised term this row's status/evidence was decided from — the concise, addable
   *  "skill name" (never the full requirement sentence) used by "Add to profile". Null when a
   *  requirement carries no normalised terms at all (nothing safe to offer as a skill name). */
  keyword: string | null;
  /** Real profile evidenceIds (experience/project) whose `technologies` already name this
   *  keyword — never invented, sourced the same way MissingKeywordsPanel already does. */
  evidenceIds: string[];
  /** True once a Skill entry with this exact name already exists in the profile — same signal
   *  `AddSkillToProfileModal` uses to skip re-asking for proficiency. */
  hasSkill: boolean;
}

/** Requirement types that describe a matchable candidate qualification — the ones it's
 *  meaningful to grade against a profile. EDUCATION and RESPONSIBILITY describe the role/degree
 *  itself, not a skill the candidate either has or doesn't, so they're surfaced in the
 *  requirements panel but never scored here. */
const ALIGNABLE_TYPES = new Set(['HARD_REQUIRED', 'PREFERRED', 'SKILL', 'TECHNOLOGY', 'CERTIFICATION']);

const normalize = (value: string): string => value.trim().toLowerCase();

/** Weak textual overlap fallback (point behind PARTIAL when there's no structured technologies
 *  match and no exact profile Skill): does any of the candidate's own skill names share the
 *  term as a whole word, in either direction. Still grounded in real skill names typed by the
 *  user — never a guess at something they didn't enter. */
function hasWeakSkillOverlap(term: string, profile: ProfileResponse): boolean {
  const target = normalize(term);
  if (target.length < 3) return false;
  return profile.skills.some((skill) => {
    const name = normalize(skill.name ?? '');
    if (!name) return false;
    return name.includes(target) || target.includes(name);
  });
}

/**
 * Client-computed, pre-generation skills alignment. There is no backend endpoint that scores a
 * job description against a profile before a resume exists (assessment-service only scores an
 * already-generated resume version — see AssessmentController) — this reuses the exact same
 * grounded-evidence lookup (`findKeywordEvidence`) the post-generation "Missing Keywords" panel
 * already relies on, applied to the JD analysis' own `normalisedTerms` instead of resume
 * keywords. Nothing here is invented: MATCHED/PARTIAL always point at real profile data.
 */
export function computeSkillsAlignment(
  requirements: Requirement[],
  profile: ProfileResponse | undefined,
): AlignmentItem[] {
  return requirements
    .filter((req) => ALIGNABLE_TYPES.has(req.type))
    .map((req) => {
      const terms = req.normalisedTerms.length > 0 ? req.normalisedTerms : [req.text];

      if (!profile) {
        return {
          requirementId: req.requirementId,
          text: req.text,
          type: req.type,
          status: 'MISSING' as const,
          keyword: req.normalisedTerms[0] ?? null,
          evidenceIds: [],
          hasSkill: false,
        };
      }

      let best: AlignmentStatus = 'MISSING';
      let bestKeyword: string | null = null;
      const evidenceIds = new Set<string>();
      let hasSkill = false;

      for (const term of terms) {
        const evidence = findKeywordEvidence(term, profile);
        if (evidence.evidenceIds.length > 0) {
          evidence.evidenceIds.forEach((id) => evidenceIds.add(id));
          if (best !== 'MATCHED') {
            best = 'MATCHED';
            bestKeyword = term;
          }
          hasSkill = hasSkill || evidence.hasSkill;
          continue;
        }
        if (evidence.hasSkill || hasWeakSkillOverlap(term, profile)) {
          hasSkill = hasSkill || evidence.hasSkill;
          if (best === 'MISSING') {
            best = 'PARTIAL';
            bestKeyword = term;
          }
        }
      }

      return {
        requirementId: req.requirementId,
        text: req.text,
        type: req.type,
        status: best,
        keyword: bestKeyword ?? req.normalisedTerms[0] ?? null,
        evidenceIds: Array.from(evidenceIds),
        hasSkill,
      };
    });
}

export const REQUIREMENT_TYPE_LABELS: Record<string, string> = {
  HARD_REQUIRED: 'Hard required',
  PREFERRED: 'Preferred',
  SKILL: 'Skill',
  TECHNOLOGY: 'Technology',
  EDUCATION: 'Education',
  CERTIFICATION: 'Certification',
  RESPONSIBILITY: 'Responsibility',
};

/** Display order + grouping for the Requirements panel — matches the redesign spec's category
 *  list (Hard Required, Preferred, Education, Responsibilities, Skills), with SKILL and
 *  TECHNOLOGY merged into one "Skills" group and CERTIFICATION folded in beside Education. */
export const REQUIREMENT_GROUPS: { id: string; label: string; types: string[] }[] = [
  { id: 'HARD_REQUIRED', label: 'Hard required', types: ['HARD_REQUIRED'] },
  { id: 'PREFERRED', label: 'Preferred', types: ['PREFERRED'] },
  { id: 'SKILLS', label: 'Skills & technology', types: ['SKILL', 'TECHNOLOGY'] },
  { id: 'EDUCATION', label: 'Education & certification', types: ['EDUCATION', 'CERTIFICATION'] },
  { id: 'RESPONSIBILITY', label: 'Responsibilities', types: ['RESPONSIBILITY'] },
];
