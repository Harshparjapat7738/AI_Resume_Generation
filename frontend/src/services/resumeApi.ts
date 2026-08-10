/**
 * resume-service — see docs/API_CATALOG.md &sect;3 (Milestone 5).
 *
 * <p><strong>Deviation from the documented contract</strong> (see
 * docs/ARCHITECTURE_DECISIONS.md ADR-013): {@code generate} is synchronous — it returns the
 * finished result directly instead of a 202 + job id to poll. There is no async job queue in
 * this milestone slice.
 */
import { apiFetch } from './apiClient';

export interface ContentBullet {
  text: string;
  evidenceIds: string[];
}

export interface ExperienceBulletGroup {
  evidenceId: string;
  bullets: ContentBullet[];
}

export interface ResumeContent {
  summary?: ContentBullet;
  experienceBullets?: ExperienceBulletGroup[];
  projectDescriptions?: unknown[];
  skillsOrdering?: string[];
}

export interface EvidenceMatch {
  requirementId: string;
  evidenceIds: string[];
  matchStrength: 'STRONG' | 'PARTIAL' | 'NONE' | string;
  reason: string;
}

export interface Gap {
  requirementId: string;
  text: string;
  type: string;
}

export interface GroundingViolation {
  rule: string;
  location: string;
  detail?: string;
}

export interface GroundingReport {
  passed: boolean;
  violations: GroundingViolation[];
  checkedStatements: number;
}

export interface ResumeVersion {
  id: string;
  jobDescriptionId: string;
  jobTitle: string | null;
  company: string | null;
  templateId: string | null;
  templateVersion: string | null;
  content: ResumeContent;
  evidenceMatches: EvidenceMatch[];
  gaps: Gap[];
  grounding: GroundingReport;
  removedSections: string[];
  createdAt: string;
}

export interface ResumeSummary {
  id: string;
  jobDescriptionId: string;
  jobTitle: string | null;
  company: string | null;
  templateId: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function generateResume(jobDescriptionId: string, templateId?: string): Promise<ResumeVersion> {
  return apiFetch<ResumeVersion>('/api/resumes/generate', {
    method: 'POST',
    body: JSON.stringify({ jobDescriptionId, templateId }),
  });
}

export function getResume(id: string): Promise<ResumeVersion> {
  return apiFetch<ResumeVersion>(`/api/resumes/${id}`);
}

export function listResumes(page = 0, size = 20): Promise<Page<ResumeSummary>> {
  return apiFetch<Page<ResumeSummary>>(`/api/resumes?page=${page}&size=${size}`);
}
