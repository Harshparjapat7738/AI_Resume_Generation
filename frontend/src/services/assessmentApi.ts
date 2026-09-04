/**
 * assessment-service — deterministic JD-fit scoring, keyed on the JD optimization (ADR-033),
 * plus the revived ATS structural score (ADR-040).
 *
 * <p>ATS scoring was removed with resume generation, then revived scoped narrowly: it scores
 * the same pre-render, cited-evidence content `ResumeRenderService` assembles for
 * `render-service` in application-service, never a rendered document — so it exists whether or
 * not that render call later succeeds. JD fit was always computed from the job description, the
 * candidate's profile and the requirement-to-evidence mapping — still deterministic, still
 * computed in Java, never asked of the LLM.
 *
 * <p>Base path is `/api/assessment` (ADR-040 — previously mismatched the backend's
 * `/api/assessment/resume-versions`, which meant every call here 404'd).
 */
import { apiFetch } from './apiClient';

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
  jdOptimizationId: string;
  jobDescriptionId: string;
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

export interface AtsCheck {
  name: string;
  label: string;
  passRatio: number;
  weight: number;
}

/** ATS structural score (ADR-040) — 0-100, rounded to one decimal (ADR-008's formula). */
export interface AtsAssessment {
  jobDescriptionId: string;
  atsScore: number;
  checks: AtsCheck[];
  assessedAt: string;
}

/**
 * Scores the JD optimization for this job description. Computes on first call; idempotent —
 * safe to call again, returns the cached result. Requires an optimization to already exist
 * (`404` otherwise).
 */
export function assessOptimization(jobDescriptionId: string): Promise<Assessment> {
  return apiFetch<Assessment>(`/api/assessment/${jobDescriptionId}`, { method: 'POST' });
}

export function getAssessment(jobDescriptionId: string): Promise<Assessment> {
  return apiFetch<Assessment>(`/api/assessment/${jobDescriptionId}`);
}

/** ATS structural score (ADR-040) — same idempotent shape as {@link assessOptimization}. */
export function assessAts(jobDescriptionId: string): Promise<AtsAssessment> {
  return apiFetch<AtsAssessment>(`/api/assessment/ats/${jobDescriptionId}`, { method: 'POST' });
}

export function getAtsAssessment(jobDescriptionId: string): Promise<AtsAssessment> {
  return apiFetch<AtsAssessment>(`/api/assessment/ats/${jobDescriptionId}`);
}
