import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import type { Template } from '@/services/templateApi';
import { DocumentPreview } from './DocumentPreview';

export interface TemplatePreviewPrimaryAction {
  label: string;
  href?: string;
  onClick?: () => void;
  disabled?: boolean;
  disabledReason?: string;
}

/**
 * The full-preview modal (redesign brief &sect;8) — the same real render `TemplateCard`/
 * `CustomTemplateCard` show in miniature, at a much larger raster width, with page navigation
 * for multi-page documents. `primaryAction` lets each caller supply its own real "Use this
 * template" behavior (a navigation for built-ins, opening the resume-picker modal for a custom
 * RESUME template, or a disabled/explained state for a custom COVER_LETTER/EMAIL template,
 * which nothing downstream can generate from yet — see CustomTemplateCard) without this modal
 * needing to know which.
 */
export function TemplatePreviewModal({
  template,
  onClose,
  primaryAction,
}: {
  template: Template;
  onClose: () => void;
  primaryAction?: TemplatePreviewPrimaryAction | undefined;
}) {
  const [page, setPage] = useState(1);
  const [numPages, setNumPages] = useState(1);

  // Reset pagination whenever a different template is opened into this same modal instance.
  useEffect(() => {
    setPage(1);
    setNumPages(1);
  }, [template.templateId]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-void/80 backdrop-blur-sm animate-toast-in" onClick={onClose} />
      <div className="animate-card-in relative flex max-h-[92vh] w-full max-w-3xl flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-2xl">
        <div className="flex items-center justify-between gap-3 border-b border-border px-6 py-4">
          <div className="min-w-0">
            <h2 className="truncate text-base font-semibold text-ink">{template.name}</h2>
            <p className="mt-0.5 text-xs text-ink-faint">
              {formatType(template.type)} · {template.source === 'CUSTOM_UPLOAD' ? 'Your upload' : 'Built-in'}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close preview"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-ink-muted hover:bg-surface-2 hover:text-ink"
          >
            ✕
          </button>
        </div>

        <div className="flex-1 overflow-y-auto bg-surface-2/60 p-6">
          <div className="mx-auto max-w-lg overflow-hidden rounded-xl border border-border bg-white shadow-2xl">
            <DocumentPreview
              templateId={template.templateId}
              page={page}
              renderWidth={1000}
              onInfo={(info) => setNumPages(info.numPages)}
            />
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-6 py-4">
          {numPages > 1 ? (
            <div className="flex items-center gap-3 text-xs text-ink-muted">
              <button
                type="button"
                disabled={page <= 1}
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                aria-label="Previous page"
                className="flex h-7 w-7 items-center justify-center rounded-full border border-border-strong disabled:opacity-30"
              >
                ‹
              </button>
              <span>
                Page {page} of {numPages}
              </span>
              <button
                type="button"
                disabled={page >= numPages}
                onClick={() => setPage((p) => Math.min(numPages, p + 1))}
                aria-label="Next page"
                className="flex h-7 w-7 items-center justify-center rounded-full border border-border-strong disabled:opacity-30"
              >
                ›
              </button>
            </div>
          ) : (
            <span />
          )}

          {primaryAction &&
            (primaryAction.href ? (
              <Link to={primaryAction.href}>
                <Button className="!px-5 !py-2.5 !text-sm">{primaryAction.label}</Button>
              </Link>
            ) : (
              <Button
                type="button"
                className="!px-5 !py-2.5 !text-sm"
                disabled={primaryAction.disabled}
                title={primaryAction.disabled ? primaryAction.disabledReason : undefined}
                onClick={primaryAction.onClick}
              >
                {primaryAction.label}
              </Button>
            ))}
        </div>
      </div>
    </div>
  );
}

function formatType(type: string): string {
  return type
    .toLowerCase()
    .split('_')
    .map((word) => word[0]?.toUpperCase() + word.slice(1))
    .join(' ');
}
