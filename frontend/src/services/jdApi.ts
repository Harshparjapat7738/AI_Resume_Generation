/**
 * jd-service — see docs/API_CATALOG.md &sect;3 (Milestone 4).
 * Text intake and SSRF-guarded URL fetch are implemented. File upload is not.
 */
import { apiFetch } from './apiClient';

export interface JdSummary {
  id: string;
  status: 'DRAFT' | 'EXTRACTED' | 'CONFIRMED' | 'REJECTED';
  sourceType: string;
  title: string | null;
  company: string | null;
  createdAt: string;
}

export interface JdDetail {
  id: string;
  status: 'DRAFT' | 'EXTRACTED' | 'CONFIRMED' | 'REJECTED';
  sourceType: string;
  sourceUrl: string | null;
  title: string | null;
  company: string | null;
  /** Only ever populated for a URL-sourced JD whose page embedded schema.org JobPosting
   *  structured data — null otherwise, never guessed. */
  location: string | null;
  skillsSummary: string | null;
  experienceSummary: string | null;
  rawText: string;
  currentVersion: number;
  createdAt: string;
}

export interface Requirement {
  requirementId: string;
  text: string;
  type: string;
  weight: number;
  normalisedTerms: string[];
}

export interface JdAnalysis {
  jobDescriptionId: string;
  title: string | null;
  company: string | null;
  seniority: string | null;
  keywords: string[];
  requirements: Requirement[];
}

export function submitJd(jobDescriptionText: string): Promise<JdSummary> {
  return apiFetch<JdSummary>('/api/jd', {
    method: 'POST',
    body: JSON.stringify({ jobDescriptionText }),
  });
}

/**
 * Fetches and extracts a job posting URL server-side (SSRF-guarded — see
 * ARCHITECTURE_DECISIONS.md ADR-015). Throws `ApiError` with code `JD_URL_BLOCKED` (unsafe
 * target) or `JD_VALIDATION_ERROR` (fetch/extraction failed) — callers show the "paste
 * instead" fallback on either.
 */
export function fetchJdFromUrl(url: string): Promise<JdSummary> {
  return apiFetch<JdSummary>('/api/jd/fetch-url', {
    method: 'POST',
    body: JSON.stringify({ url }),
  });
}

export function getJd(id: string): Promise<JdDetail> {
  return apiFetch<JdDetail>(`/api/jd/${id}`);
}

/**
 * The Review step's "Edit JD" action — replaces the pre-confirmation text with a new version
 * (jd-service keeps every version, but only the latest one is ever shown or confirmed against —
 * see docs/DATABASE.md's `jd_versions`). `409 CONFLICT` (via `ApiError`) once the job description
 * has already been confirmed; callers should only ever offer editing before that.
 */
export function editJd(id: string, jobDescriptionText: string): Promise<JdDetail> {
  return apiFetch<JdDetail>(`/api/jd/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ jobDescriptionText }),
  });
}

export function confirmJd(id: string): Promise<JdSummary> {
  return apiFetch<JdSummary>(`/api/jd/${id}/confirm`, { method: 'POST' });
}

/** May take several seconds on first call — this is when the JD is actually analysed by Groq. */
export function getAnalysis(id: string): Promise<JdAnalysis> {
  return apiFetch<JdAnalysis>(`/api/jd/${id}/analysis`);
}

// ---- JD optimization (ADR-033) ---------------------------------------------

/** One JD term the posting cares about, and the caller's own evidence backing it. An empty
 *  `evidenceIds` means the profile does not support it — a gap, never something to claim. */
export interface OptimizationKeyword {
  term: string;
  priority: 'REQUIRED' | 'PREFERRED';
  category?: string | null;
  evidenceIds: string[];
}

export interface OptimizationRequirementMatch {
  requirementId: string;
  evidenceIds: string[];
  matchStrength: 'STRONG' | 'PARTIAL' | 'NONE';
  reason?: string;
}

export interface OptimizationEmphasis {
  evidenceId: string;
  rank: number;
  rationale?: string;
}

export interface OptimizationData {
  targetRole?: string | null;
  targetCompany?: string | null;
  keywords: OptimizationKeyword[];
  requirementMatches: OptimizationRequirementMatch[];
  missingRequirements: { requirementId: string; note?: string }[];
  emphasis: OptimizationEmphasis[];
}

export interface JdOptimization {
  id: string;
  jobDescriptionId: string;
  optimisation: OptimizationData;
  /** Every evidence id the result cites — the provenance trail for the whole document. */
  citedEvidenceIds: string[];
  createdAt: string;
}

/**
 * Optimises the user's verified profile against a confirmed JD — the operation that replaced
 * resume/cover-letter generation. Returns structured targeting data, not a document.
 *
 * May take several seconds: this is where Groq runs. Re-reads an existing result for the same JD
 * version rather than spending another AI request; pass `refresh` after a profile edit, when the
 * JD is unchanged but the evidence it's matched against is not.
 */
export function optimizeForJd(id: string, refresh = false): Promise<JdOptimization> {
  return apiFetch<JdOptimization>(`/api/jd/${id}/optimize?refresh=${refresh}`, { method: 'POST' });
}

/** The current optimization for this JD. `ApiError` with status 404 when none exists yet. */
export function getJdOptimization(id: string): Promise<JdOptimization> {
  return apiFetch<JdOptimization>(`/api/jd/${id}/optimization`);
}
