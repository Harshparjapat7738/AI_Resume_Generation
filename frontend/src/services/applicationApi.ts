/**
 * application-service — see docs/API_CATALOG.md &sect;3 (Milestone 8) and
 * ARCHITECTURE_DECISIONS.md ADR-017 (the central `Application` aggregate) / ADR-019 (email
 * generation) / ADR-020 (cover-letter generation).
 */
import { apiFetch } from './apiClient';

export type GenerationType = 'RESUME_ONLY' | 'COVER_LETTER_ONLY' | 'EMAIL_ONLY' | 'ALL';
export type ApplicationStatus = 'DRAFT' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

/** One of the three outputs `GenerationType.ALL` ("Generate All") tracks independently — see
 *  `Application.recordOutputFailure` (ARCHITECTURE_DECISIONS.md, "Generate All" ADR). */
export type ApplicationOutput = 'resume' | 'coverLetter' | 'email';

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
  /** Set (and independently retryable) when this specific output failed to generate —
   *  cleared the moment that output's own generate call next succeeds. Distinct outputs
   *  fail and recover independently: one being set never implies the others are. */
  resumeError: string | null;
  coverLetterError: string | null;
  emailError: string | null;
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
  templateId: string | null;
  status: ApplicationStatus;
  /** Populated only for `ALL` rows today — null for the other generation types, which only
   *  ever populate the one field relevant to them. Lets the dashboard render per-output
   *  status without an extra request per row. */
  resumeVersionId: string | null;
  coverLetterVersionId: string | null;
  emailId: string | null;
  resumeError: string | null;
  coverLetterError: string | null;
  emailError: string | null;
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

/** Attaches (or replaces) the generated resume reference on an existing application — the
 *  same reference-recording pattern `generateEmail`/`generateCoverLetter` use, except resume
 *  Legacy: resume generation was removed (ADR-033); this only
 *  records the resulting `resumeVersionId`, used by the "Generate All" flow. */
export function attachResume(applicationId: string, resumeVersionId: string): Promise<Application> {
  return apiFetch<Application>(`/api/applications/${applicationId}/resume`, {
    method: 'POST',
    body: JSON.stringify({ resumeVersionId }),
  });
}

/** Records that one output of a "Generate All" application failed to generate, so the
 *  failure — and its reason — survives a refresh exactly like a success does. Call this from
 *  the `catch` of whichever call actually failed (resume-service's generate, or this
 *  service's own `generateEmail`/`generateCoverLetter`); it never throws for a well-formed
 *  request, so it's safe to call from a catch block without another try/catch around it. */
export function recordOutputFailure(
  applicationId: string,
  output: ApplicationOutput,
  reason: string,
): Promise<Application> {
  return apiFetch<Application>(`/api/applications/${applicationId}/outputs/${output}/failed`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
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

export interface FallbackPrompt {
  coverLetterVersionId: string;
  outputFormat: string;
  prompt: string;
}

/**
 * The document-generation fallback prompt (see application-service's `FallbackPromptBuilder`,
 * mirroring resume-service's own) — a complete, ready-to-paste external-AI prompt built
 * deterministically from this cover-letter version's already-validated content. Called only
 * after document-service has failed to produce a PDF/DOCX after its allowed retry — never
 * calls Groq/Gemini, never regenerates anything.
 */
export function getCoverLetterFallbackPrompt(
  coverLetterVersionId: string,
  format: 'PDF' | 'DOCX' = 'PDF',
): Promise<FallbackPrompt> {
  return apiFetch<FallbackPrompt>(`/api/applications/cover-letter-versions/${coverLetterVersionId}/fallback-prompt?format=${format}`);
}

/** Paged application history for the /dashboard screen. */
export function listApplications(status?: ApplicationStatus, page = 0, size = 20): Promise<Page<ApplicationSummary>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  return apiFetch<Page<ApplicationSummary>>(`/api/applications?${params.toString()}`);
}
