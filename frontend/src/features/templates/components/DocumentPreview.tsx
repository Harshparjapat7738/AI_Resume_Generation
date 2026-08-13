import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getTemplatePreview } from '@/services/documentApi';
import { DocxHtmlPreview } from './DocxHtmlPreview';
import { PdfPagePreview } from './PdfPagePreview';

export interface DocumentPreviewInfo {
  format: 'PDF' | 'DOCX';
  numPages: number;
}

const DOCX_MIME = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

/**
 * The single entry point for "what does this template actually look like" (redesign brief
 * &sect;2/14/25) — fetches the real render (`documentApi.getTemplatePreview`, the same
 * PdfRenderer/mail-merge pipeline real generation uses) and hands the bytes to whichever viewer
 * matches the response's real content type: `PdfPagePreview` (pdf.js) for built-in templates and
 * PDF-sourced custom uploads, `DocxHtmlPreview` (docx-preview) for DOCX-sourced custom uploads.
 * Never a CSS mockup, never guessed.
 */
export function DocumentPreview({
  templateId,
  page = 1,
  renderWidth,
  onInfo,
  className,
}: {
  templateId: string;
  /** 1-indexed page to display — ignored (whole document renders) until the viewer resolves at
   *  least one page/section, then clamped to the real page count. */
  page?: number;
  /** Internal PDF raster width in CSS px — higher for the full-preview modal than for a card. */
  renderWidth?: number | undefined;
  onInfo?: ((info: DocumentPreviewInfo) => void) | undefined;
  className?: string | undefined;
}) {
  const queryClient = useQueryClient();
  const [renderFailed, setRenderFailed] = useState(false);

  const query = useQuery({
    queryKey: ['template-preview-blob', templateId],
    queryFn: () => getTemplatePreview(templateId),
    // Deterministic: the same templateId always renders the same sample-data preview, so once
    // fetched it never needs to be refetched just because time passed.
    staleTime: Infinity,
    retry: 1,
  });

  const retry = () => {
    setRenderFailed(false);
    void queryClient.invalidateQueries({ queryKey: ['template-preview-blob', templateId] });
  };

  if (query.isLoading) {
    return <PreviewSkeleton className={className} />;
  }

  if (query.isError || !query.data || renderFailed) {
    return <PreviewError onRetry={retry} className={className} />;
  }

  const isDocx = query.data.type === DOCX_MIME;
  const handleReady = (info: { numPages: number }) => onInfo?.({ format: isDocx ? 'DOCX' : 'PDF', numPages: info.numPages });
  const handleError = () => setRenderFailed(true);

  return (
    <div className={className}>
      {isDocx ? (
        <DocxHtmlPreview blob={query.data} page={page} onDocumentReady={handleReady} onError={handleError} />
      ) : (
        <PdfPagePreview
          blob={query.data}
          page={page}
          renderWidth={renderWidth}
          onDocumentReady={handleReady}
          onError={handleError}
        />
      )}
    </div>
  );
}

/** Skeleton document preview (redesign spec &sect;21) — shown while the real render is in
 *  flight, never a blank card. */
function PreviewSkeleton({ className }: { className?: string | undefined }) {
  return (
    <div className={`flex animate-pulse flex-col gap-2 bg-white p-6 ${className ?? ''}`}>
      <div className="h-3 w-1/2 rounded bg-gray-200" />
      <div className="h-2 w-1/3 rounded bg-gray-200" />
      <div className="mt-4 h-2 w-full rounded bg-gray-100" />
      <div className="h-2 w-full rounded bg-gray-100" />
      <div className="h-2 w-2/3 rounded bg-gray-100" />
      <div className="mt-4 h-2 w-full rounded bg-gray-100" />
      <div className="h-2 w-full rounded bg-gray-100" />
      <div className="h-2 w-4/5 rounded bg-gray-100" />
    </div>
  );
}

/** Preview-generation-failed state (redesign spec &sect;22) — a real retry (re-fetches the real
 *  render), never a dead end; the card around this still offers "Use template". */
function PreviewError({ onRetry, className }: { onRetry: () => void; className?: string | undefined }) {
  return (
    <div className={`flex flex-col items-center justify-center gap-3 bg-surface-2 p-6 text-center ${className ?? ''}`}>
      <p className="text-sm text-ink-muted">Preview unavailable</p>
      <button
        type="button"
        onClick={(event) => {
          // Cards wrap the whole preview in a click-to-open-modal region — stop this from also
          // triggering that when the failure state's own Retry is what the user meant to click.
          event.stopPropagation();
          onRetry();
        }}
        className="rounded-full border border-border-strong px-3 py-1.5 text-xs font-medium text-ink transition-colors hover:border-ink-muted"
      >
        Retry
      </button>
    </div>
  );
}
