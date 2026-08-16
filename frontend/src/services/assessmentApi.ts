/**
 * assessment-service — deterministic JD-fit scoring, keyed on the JD optimization (ADR-033).
 *
 * <p>ATS scoring was removed with resume generation: every one of its checks read a rendered
 * resume's structure (section headings, bullet lengths, formatting), and no resume is produced
 * any more. What survives is JD fit, which was always computed from the job description, the
 * candidate's profile and the requirement-to-evidence mapping — still deterministic, still
 * computed in Java, never asked of the LLM.
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
