/**
 * document-service — real Resume PDF generation (docs/API_CATALOG.md &sect;2). The
 * selectable template catalogue lives in resume-service (see templateApi.ts) — this client
 * only renders and downloads against a resumeVersionId, using whatever templateId that
 * resume version was generated with unless explicitly overridden.
 */
import { apiFetch, apiFetchBlob } from './apiClient';

export interface RenderedDocument {
  id: string;
  resumeVersionId: string;
  format: string;
  templateId: string;
  templateVersion: string;
  pageCount: number;
  byteSize: number;
  sha256: string;
  renderedAt: string;
}

/** Renders (or re-renders) the resume version's PDF. Omitting templateId uses whatever
 *  template that resume version was generated with. */
export function renderResumePdf(resumeVersionId: string, templateId?: string): Promise<RenderedDocument> {
  return apiFetch<RenderedDocument>(`/api/documents/resume-versions/${resumeVersionId}/render`, {
    method: 'POST',
    body: JSON.stringify({ templateId }),
  });
}

/** 404s (ApiError with status 404) if this resume version has never been rendered. */
export function getRenderedDocument(resumeVersionId: string): Promise<RenderedDocument> {
  return apiFetch<RenderedDocument>(`/api/documents/resume-versions/${resumeVersionId}`);
}

export function downloadDocument(documentId: string): Promise<Blob> {
  return apiFetchBlob(`/api/documents/${documentId}/download`);
}
