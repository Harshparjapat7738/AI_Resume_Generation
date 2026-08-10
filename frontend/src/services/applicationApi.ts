/**
 * application-service — see docs/API_CATALOG.md &sect;3 (Milestone 8) and
 * ARCHITECTURE_DECISIONS.md ADR-017 (the central `Application` aggregate) / ADR-019 (email
 * generation) / ADR-020 (cover-letter generation).
 */
import { apiFetch } from './apiClient';

export type GenerationType = 'RESUME_ONLY' | 'COVER_LETTER_ONLY' | 'EMAIL_ONLY' | 'ALL';
export type ApplicationStatus = 'DRAFT' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface Application {
  id: string;
  jobDescriptionId: string;
  jobTitle: string | null;
  company: string | null;
  generationType: GenerationType;
  templateId: string | null;
  resumeVersionId: string | null;
  coverLetterVersionId: string | null;
  emailId: string | null;
  assessed: boolean;
  status: ApplicationStatus;
  failureCode: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EmailHighlight {
  text: string;
  evidenceIds: string[];
}

export interface EmailContent {
  id: string;
  applicationId: string;
  subject: string;
  body: string;
  highlights: EmailHighlight[];
  grounding: Record<string, unknown> | null;
  removedParagraphs: string[];
  version: number;
  createdAt: string;
}

export interface CoverLetterParagraph {
  text: string;
  evidenceIds: string[];
}

/** Every field is optional: the grounding degrade path can drop a paragraph entirely rather
 *  than ship it unverified — see docs/ARCHITECTURE_DECISIONS.md ADR-020. */
export interface CoverLetterContent {
  greeting?: string;
  openingParagraph?: CoverLetterParagraph;
  bodyParagraphs?: CoverLetterParagraph[];
  closingParagraph?: CoverLetterParagraph;
  signOff?: string;
}

export interface CoverLetterVersion {
  id: string;
  applicationId: string;
  jobDescriptionId: string;
  jobTitle: string | null;
  company: string | null;
  version: number;
  content: CoverLetterContent;
  grounding: Record<string, unknown> | null;
  removedParagraphs: string[];
  createdAt: string;
}

export interface ApplicationSummary {
  id: string;
  jobDescriptionId: string;
  jobTitle: string | null;
  company: string | null;
  generationType: GenerationType;
  status: ApplicationStatus;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function createApplication(
  jobDescriptionId: string,
  generationType: GenerationType,
  templateId?: string,
  resumeVersionId?: string,
): Promise<Application> {
  return apiFetch<Application>('/api/applications', {
    method: 'POST',
    body: JSON.stringify({ jobDescriptionId, generationType, templateId, resumeVersionId }),
  });
}

export function getApplication(id: string): Promise<Application> {
  return apiFetch<Application>(`/api/applications/${id}`);
}

/** Generates (first call) or regenerates (every subsequent call) the application email — a
 *  new version is persisted each time, mirroring resume-service's generate-again pattern. */
export function generateEmail(applicationId: string): Promise<EmailContent> {
  return apiFetch<EmailContent>(`/api/applications/${applicationId}/email`, { method: 'POST' });
}

/** The latest generated email. `404` if none has been generated yet. */
export function getEmail(applicationId: string): Promise<EmailContent> {
  return apiFetch<EmailContent>(`/api/applications/${applicationId}/email`);
}

/** Generates (first call) or regenerates (every subsequent call) the cover letter — a new
 *  version is persisted each time, mirroring resume-service's and `generateEmail`'s
 *  generate-again pattern. */
export function generateCoverLetter(applicationId: string): Promise<CoverLetterVersion> {
  return apiFetch<CoverLetterVersion>(`/api/applications/${applicationId}/cover-letter`, { method: 'POST' });
}

/** The latest generated cover letter. `404` if none has been generated yet. */
export function getCoverLetter(applicationId: string): Promise<CoverLetterVersion> {
  return apiFetch<CoverLetterVersion>(`/api/applications/${applicationId}/cover-letter`);
}

/** Paged application history for the /dashboard screen. */
export function listApplications(status?: ApplicationStatus, page = 0, size = 20): Promise<Page<ApplicationSummary>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  return apiFetch<Page<ApplicationSummary>>(`/api/applications?${params.toString()}`);
}
