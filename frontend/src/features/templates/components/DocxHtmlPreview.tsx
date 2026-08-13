import { useEffect, useRef } from 'react';

/** Dynamically imported (see `pdfWorker.ts`'s comment on `loadPdfjs` for the same rationale) —
 *  docx-preview only ever needs to load into a session that actually opens a DOCX-sourced
 *  custom template's preview. */
function loadDocxPreview(): Promise<typeof import('docx-preview')> {
  return import('docx-preview');
}

/**
 * Renders a real, already-merged DOCX (a DOCX-sourced custom template — see
 * `documentApi.getTemplatePreview`) as HTML via docx-preview, directly into a container div —
 * the actual document's fonts/spacing/tables/headers, not an approximation, because the bytes
 * being rendered are the real mail-merge output (`DocumentRenderService#renderPreview` →
 * `CustomTemplateAssetService#generatePreview`), same code path as real generation. Styles are
 * scoped to the container itself (docx-preview falls back to using the body container as its
 * own style container when none is passed), so this never leaks into the rest of the page.
 *
 * Pagination here is "scroll to page N" rather than pdf.js's "render only page N" — docx-preview
 * renders the whole flowing document in one pass, so page navigation only needs to scroll an
 * already-rendered section into view, not re-render anything.
 */
export function DocxHtmlPreview({
  blob,
  page = 1,
  onDocumentReady,
  onError,
}: {
  blob: Blob;
  page?: number | undefined;
  onDocumentReady?: ((info: { numPages: number }) => void) | undefined;
  onError?: (() => void) | undefined;
}) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    let cancelled = false;
    container.innerHTML = '';

    loadDocxPreview()
      .then(({ renderAsync }) =>
        renderAsync(blob, container, undefined, {
          inWrapper: true,
          ignoreWidth: false,
          ignoreHeight: false,
          breakPages: true,
          renderHeaders: true,
          renderFooters: true,
        }),
      )
      .then(() => {
        if (cancelled) return;
        const sections = container.querySelectorAll('.docx-wrapper > section.docx');
        onDocumentReady?.({ numPages: Math.max(1, sections.length) });
      })
      .catch(() => {
        if (!cancelled) onError?.();
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [blob]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const sections = container.querySelectorAll<HTMLElement>('.docx-wrapper > section.docx');
    sections[page - 1]?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [page]);

  return <div ref={containerRef} className="docx-preview-host w-full" />;
}
