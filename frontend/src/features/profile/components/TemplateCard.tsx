import { useEffect, useRef, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { DocumentIcon, DownloadIcon, EyeIcon, StarIcon } from '@/features/dashboard/icons';
import type { TemplateResponse } from '@/services/templateApi';

const DOCUMENT_TYPE_LABEL: Record<TemplateResponse['documentType'], string> = {
  RESUME: 'Resume',
  COVER_LETTER: 'Cover Letter',
  BOTH: 'Resume + Cover Letter',
};

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  return kb < 1024 ? `${kb.toFixed(0)} KB` : `${(kb / 1024).toFixed(1)} MB`;
}

/** The "⋮" overflow menu — Rename / Set Default / Delete. Same open/close/focus/click-outside
 *  behaviour as the account menu in the header (UserMenu.tsx), scaled down to a card action. */
function ActionsMenu({
  templateName,
  isDefault,
  onRename,
  onSetDefault,
  onDelete,
}: {
  templateName: string;
  isDefault: boolean;
  onRename: () => void;
  onSetDefault: () => void;
  onDelete: () => void;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const runAndClose = (action: () => void) => {
    setOpen(false);
    action();
  };

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`${templateName} actions`}
        className="flex h-8 w-8 items-center justify-center rounded-full text-lg leading-none text-ink-faint transition-colors hover:bg-surface-2 hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember-soft"
      >
        ⋮
      </button>
      {open && (
        <div
          role="menu"
          aria-label={`${templateName} actions`}
          className="absolute right-0 top-full z-10 mt-2 w-44 origin-top-right rounded-xl border border-border bg-surface p-1.5 shadow-xl shadow-black/40"
        >
          <button
            type="button"
            role="menuitem"
            onClick={() => runAndClose(onRename)}
            className="flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink"
          >
            Rename
          </button>
          {!isDefault && (
            <button
              type="button"
              role="menuitem"
              onClick={() => runAndClose(onSetDefault)}
              className="flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink"
            >
              Set as default
            </button>
          )}
          <button
            type="button"
            role="menuitem"
            onClick={() => runAndClose(onDelete)}
            className="flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-rose transition-colors hover:bg-rose/10"
          >
            Delete
          </button>
        </div>
      )}
    </div>
  );
}

export function TemplateCard({
  template,
  busy = false,
  onPreview,
  onDownload,
  onRename,
  onSetDefault,
  onDelete,
}: {
  template: TemplateResponse;
  /** True while this card's own preview/download blob fetch is in flight — disables both
   *  buttons so a slow connection can't queue up several overlapping object-URL downloads. */
  busy?: boolean;
  onPreview: () => void;
  onDownload: () => void;
  onRename: () => void;
  onSetDefault: () => void;
  onDelete: () => void;
}) {
  return (
    <Card className="!p-5 transition-colors hover:border-border-strong">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-surface-2 text-ember-soft ring-1 ring-border-strong">
            <DocumentIcon className="h-5 w-5" />
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-ink">{template.name}</p>
            <p className="mt-0.5 text-xs text-ink-faint">
              {template.fileType} • {DOCUMENT_TYPE_LABEL[template.documentType]} • {formatBytes(template.byteSize)}
            </p>
          </div>
        </div>
        <ActionsMenu
          templateName={template.name}
          isDefault={template.isDefault}
          onRename={onRename}
          onSetDefault={onSetDefault}
          onDelete={onDelete}
        />
      </div>

      {template.isDefault && (
        <span className="mt-3 inline-flex items-center gap-1 rounded-full bg-mint/10 px-2.5 py-1 text-xs font-medium text-mint">
          <StarIcon className="h-3 w-3" />
          Default
        </span>
      )}

      <div className="mt-4 flex gap-2">
        <button
          type="button"
          onClick={onPreview}
          disabled={busy}
          className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-full border border-border-strong px-3 py-2 text-xs font-medium text-ink transition-colors hover:border-ember-soft disabled:cursor-not-allowed disabled:opacity-60"
        >
          <EyeIcon className="h-3.5 w-3.5" />
          Preview
        </button>
        <button
          type="button"
          onClick={onDownload}
          disabled={busy}
          className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-full border border-border-strong px-3 py-2 text-xs font-medium text-ink transition-colors hover:border-ember-soft disabled:cursor-not-allowed disabled:opacity-60"
        >
          <DownloadIcon className="h-3.5 w-3.5" />
          Download
        </button>
      </div>
    </Card>
  );
}
