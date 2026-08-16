/**
 * profile-service's "My Templates" library (ADR-034) — a user's own uploaded Resume/Cover
 * Letter files, uploaded once from Profile → My Templates and reused (never re-uploaded) at
 * JD-optimization handoff time. See docs/API_CATALOG.md.
 */
import { apiFetch, apiFetchBlob } from './apiClient';

export type TemplateFileType = 'PDF' | 'DOCX';
export type TemplateDocumentType = 'RESUME' | 'COVER_LETTER' | 'BOTH';

export interface TemplateResponse {
  id: string;
  name: string;
  fileName: string;
  fileType: TemplateFileType;
  documentType: TemplateDocumentType;
  isDefault: boolean;
  byteSize: number;
  createdAt: string;
  updatedAt: string;
}

export function listTemplates(): Promise<TemplateResponse[]> {
  return apiFetch<TemplateResponse[]>('/api/profile/templates');
}

export function uploadTemplate(input: {
  file: File;
  name?: string | undefined;
  documentType: TemplateDocumentType;
}): Promise<TemplateResponse> {
  const form = new FormData();
  form.append('file', input.file);
  if (input.name) form.append('name', input.name);
  form.append('documentType', input.documentType);
  // apiFetch already skips the JSON Content-Type header for a FormData body, letting the
  // browser set the multipart boundary itself — see apiClient.ts's buildHeaders.
  return apiFetch<TemplateResponse>('/api/profile/templates', { method: 'POST', body: form });
}

export function renameTemplate(id: string, name: string): Promise<TemplateResponse> {
  return apiFetch<TemplateResponse>(`/api/profile/templates/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  });
}

export function setDefaultTemplate(id: string): Promise<TemplateResponse> {
  return apiFetch<TemplateResponse>(`/api/profile/templates/${id}/default`, { method: 'POST' });
}

export function deleteTemplate(id: string): Promise<void> {
  return apiFetch<void>(`/api/profile/templates/${id}`, { method: 'DELETE' });
}

/** Raw bytes, exactly as uploaded — no rendering, no transformation (ADR-034). Used for both
 *  "Preview" (opened in a new tab via an object URL) and "Download" (saved via a synthetic
 *  anchor click) — see MyTemplatesPage.tsx. */
export function downloadTemplate(id: string): Promise<Blob> {
  return apiFetchBlob(`/api/profile/templates/${id}/download`);
}
