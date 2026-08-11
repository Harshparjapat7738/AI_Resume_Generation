/**
 * resume-service — selectable template catalogue (docs/API_CATALOG.md &sect;2 "resume-service";
 * ARCHITECTURE_DECISIONS.md ADR-004, ADR-016). Built-in templates are seeded, always
 * `source: 'BUILT_IN'`. Custom-uploaded templates (`source: 'CUSTOM_UPLOAD'`) are real user
 * data — uploaded, analyzed and mapped through the endpoints below — mixed into the same list
 * `listTemplates` already returned, scoped to the caller (never another user's upload).
 */
import { apiFetch } from './apiClient';

export type TemplateDocumentType = 'RESUME' | 'COVER_LETTER' | 'EMAIL';
export type TemplateStatus = 'ACTIVE' | 'DISABLED';
export type TemplateSource = 'BUILT_IN' | 'CUSTOM_UPLOAD' | 'ONLINE';

/** Real structural facts document-service's analyzer read straight off the uploaded .docx —
 *  see DocxStructureAnalyzer. Twips are DOCX's native unit (1440 = 1 inch). `null` only for
 *  built-in templates, which have no uploaded file to analyze. */
export interface TemplateStructure {
  pageWidthTwips: number | null;
  pageHeightTwips: number | null;
  marginTopTwips: number | null;
  marginBottomTwips: number | null;
  marginLeftTwips: number | null;
  marginRightTwips: number | null;
  columnCount: number;
  fontsUsed: string[];
  fontSizesPt: number[];
  colorsUsedHex: string[];
  alignmentsUsed: string[];
  headingsFound: string[];
  paragraphCount: number;
  tableCount: number;
  hasHeader: boolean;
  hasFooter: boolean;
}

export interface DetectedField {
  token: string;
  context: string;
  suggestedField: string | null;
}

export interface Template {
  templateId: string;
  name: string;
  description: string;
  /** Key the frontend maps to a real local preview component — not an image URL: no
   *  thumbnail-rendering pipeline exists (ADR-016). Null for custom uploads — those show
   *  `structure` instead. */
  previewKey: string | null;
  type: TemplateDocumentType;
  version: string;
  status: TemplateStatus;
  source: TemplateSource;
  supportedFormats: string[];
  atsSafe: boolean;
  // ---- custom-upload-only fields, null/empty for BUILT_IN and ONLINE ----
  ownerUserId: string | null;
  originalFilename: string | null;
  structure: TemplateStructure | null;
  detectedFields: DetectedField[] | null;
  /** token -> profile field key (see PROFILE_FIELDS below), editable by the owner. */
  fieldMappings: Record<string, string> | null;
  isDefault: boolean;
  createdAt: string;
}

/** Mirrors document-service's `ProfileFieldCatalog` — the fixed set of profile-derived fields
 *  a placeholder can map onto. Kept here (not fetched) since it's a small, stable, non-user
 *  set — identical to what the backend already enforces at generation time. */
export const PROFILE_FIELDS: { key: string; label: string }[] = [
  { key: 'NAME', label: 'Full Name' },
  { key: 'EMAIL', label: 'Email' },
  { key: 'PHONE', label: 'Phone' },
  { key: 'HEADLINE', label: 'Professional Headline' },
  { key: 'SUMMARY', label: 'Professional Summary' },
  { key: 'EXPERIENCE', label: 'Experience' },
  { key: 'EDUCATION', label: 'Education' },
  { key: 'SKILLS', label: 'Skills' },
  { key: 'PROJECTS', label: 'Projects' },
  { key: 'CERTIFICATIONS', label: 'Certifications' },
  { key: 'ACHIEVEMENTS', label: 'Achievements' },
  { key: 'LINKS', label: 'Links' },
];

export function listTemplates(type: TemplateDocumentType = 'RESUME'): Promise<Template[]> {
  return apiFetch<Template[]>(`/api/resumes/templates?type=${type}`);
}

export function getTemplate(id: string): Promise<Template> {
  return apiFetch<Template>(`/api/resumes/templates/${id}`);
}

/** Step 1 of the upload wizard: choose file + type + name. The response already carries the
 *  real analysis (structure, detected placeholders, suggested mapping) — no separate "analyze"
 *  call needed before the review/mapping steps. */
export function uploadCustomTemplate(file: File, type: TemplateDocumentType, name: string): Promise<Template> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('type', type);
  formData.append('name', name);
  return apiFetch<Template>('/api/resumes/templates/custom', { method: 'POST', body: formData });
}

export function renameCustomTemplate(id: string, name: string): Promise<Template> {
  return apiFetch<Template>(`/api/resumes/templates/custom/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  });
}

export function updateTemplateMapping(id: string, mappings: Record<string, string>): Promise<Template> {
  return apiFetch<Template>(`/api/resumes/templates/custom/${id}/mapping`, {
    method: 'PUT',
    body: JSON.stringify({ mappings }),
  });
}

export function duplicateCustomTemplate(id: string): Promise<Template> {
  return apiFetch<Template>(`/api/resumes/templates/custom/${id}/duplicate`, { method: 'POST' });
}

export function deleteCustomTemplate(id: string): Promise<void> {
  return apiFetch<void>(`/api/resumes/templates/custom/${id}`, { method: 'DELETE' });
}

export function setDefaultTemplate(id: string): Promise<Template> {
  return apiFetch<Template>(`/api/resumes/templates/custom/${id}/default`, { method: 'POST' });
}

export function unsetDefaultTemplate(id: string): Promise<Template> {
  return apiFetch<Template>(`/api/resumes/templates/custom/${id}/default`, { method: 'DELETE' });
}
