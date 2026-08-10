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

export function confirmJd(id: string): Promise<JdSummary> {
  return apiFetch<JdSummary>(`/api/jd/${id}/confirm`, { method: 'POST' });
}

/** May take several seconds on first call — this is when the JD is actually analysed by Groq. */
export function getAnalysis(id: string): Promise<JdAnalysis> {
  return apiFetch<JdAnalysis>(`/api/jd/${id}/analysis`);
}
