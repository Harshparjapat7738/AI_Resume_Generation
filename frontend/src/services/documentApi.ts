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

/**
 * Mail-merges a custom-uploaded template with a real, already-generated resume version's
 * content — the DOCX-preserving path custom templates use instead of the built-in Thymeleaf/
 * PDF pipeline (see document-service's DocxMailMerge). Returns the same shape as
 * {@link renderResumePdf} — `format` is `"DOCX"` — so the exact same {@link downloadDocument}
 * call downloads it.
 */
export function generateFromCustomTemplate(
  templateId: string,
  resumeVersionId: string,
  fieldMappings: Record<string, string>,
): Promise<RenderedDocument> {
  return apiFetch<RenderedDocument>(`/api/documents/custom-templates/${templateId}/generate`, {
    method: 'POST',
    body: JSON.stringify({ resumeVersionId, fieldMappings }),
  });
}
