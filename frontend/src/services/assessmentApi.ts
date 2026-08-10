/**
 * assessment-service — see docs/API_CATALOG.md &sect;3 (Milestone 7).
 *
 * <p><strong>Scope deviation</strong> (ARCHITECTURE_DECISIONS.md ADR-014): the ATS checks
 * here score the generated resume's structured content, not a rendered PDF/DOCX — no
 * document-service exists yet to produce one. Still deterministic, still computed in Java,
 * never asked of the LLM.
 */
import { apiFetch } from './apiClient';

export interface AtsCheck {
  checkId: string;
  label: string;
  weight: number;
  passRatio: number;
  detail: string;
  earned: number;
}

export interface RequirementMatch {
  requirementId: string;
  text: string;
  type: string;
  matchStrength: 'STRONG' | 'PARTIAL' | 'NONE' | string;
  evidenceIds: string[];
}

export interface Recommendation {
  type: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW' | string;
  message: string;
  relatedRequirementId: string | null;
}

export interface Assessment {
  resumeVersionId: string;
  atsScore: number;
  atsChecks: AtsCheck[];
  engineVersion: string;
  compatibilityScore: number;
  coverage: number;
  keywordMatch: number;
  seniorityMatch: number;
  recency: number;
  requirementMatches: RequirementMatch[];
  unmetHardRequirements: RequirementMatch[];
  matchedKeywords: string[];
  missingKeywords: string[];
  readinessBand: 'STRONG' | 'COMPETITIVE' | 'STRETCH' | 'WEAK_FIT' | string;
  bandRule: string;
  recommendations: Recommendation[];
  assessedAt: string;
}

/** Computes on first call; idempotent — safe to call again, returns the cached result. */
export function assessResume(resumeVersionId: string): Promise<Assessment> {
  return apiFetch<Assessment>(`/api/assessment/resume-versions/${resumeVersionId}`, { method: 'POST' });
}

export function getAssessment(resumeVersionId: string): Promise<Assessment> {
  return apiFetch<Assessment>(`/api/assessment/resume-versions/${resumeVersionId}`);
}
