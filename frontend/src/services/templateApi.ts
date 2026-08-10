/**
 * resume-service — selectable template catalogue (docs/API_CATALOG.md &sect;2 "resume-service";
 * ARCHITECTURE_DECISIONS.md ADR-004, ADR-016). Only built-in templates exist today — custom
 * upload and online templates are not implemented, and this client has no functions for them.
 */
import { apiFetch } from './apiClient';

export type TemplateDocumentType = 'RESUME' | 'COVER_LETTER' | 'EMAIL';
export type TemplateStatus = 'ACTIVE' | 'DISABLED';
export type TemplateSource = 'BUILT_IN' | 'CUSTOM_UPLOAD' | 'ONLINE';

export interface Template {
  templateId: string;
  name: string;
  description: string;
  /** Key the frontend maps to a real local preview component — see TemplatePreview.tsx. Not
   *  an image URL: no thumbnail-rendering pipeline exists (ADR-016). */
  previewKey: string;
  type: TemplateDocumentType;
  version: string;
  status: TemplateStatus;
  source: TemplateSource;
  supportedFormats: string[];
  atsSafe: boolean;
}

export function listTemplates(type: TemplateDocumentType = 'RESUME'): Promise<Template[]> {
  return apiFetch<Template[]>(`/api/resumes/templates?type=${type}`);
}

export function getTemplate(id: string): Promise<Template> {
  return apiFetch<Template>(`/api/resumes/templates/${id}`);
}
