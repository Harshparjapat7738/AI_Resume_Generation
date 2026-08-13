import { Card } from '@/components/ui/Card';
import { CopyIcon, DownloadIcon, LockIcon, RefreshIcon } from '@/features/dashboard/icons';
import type { Assessment } from '@/services/assessmentApi';

const bandStyle: Record<string, string> = {
  STRONG: 'bg-mint/10 text-mint',
  COMPETITIVE: 'bg-ember-soft/10 text-ember-soft',
  STRETCH: 'bg-ember-soft/10 text-ember-soft',
  WEAK_FIT: 'bg-rose/10 text-rose',
};

const bandLabel: Record<string, string> = {
  STRONG: 'Strong fit',
  COMPETITIVE: 'Competitive fit',
  STRETCH: 'Stretch fit',
  WEAK_FIT: 'Weak fit',
};

/** The large, page-specific "what am I looking at" card (point 4 of the redesign spec) — a
 *  layer above the compact, generic `PageHeader` above it. Every action here is the exact same
 *  handler ResultPage already had; this component only owns presentation. */
export function ResultHero({
  jobTitle,
  company,
  createdAt,
  templateName,
  assessment,
  onCopy,
  copyLabel,
  onDownload,
  downloadPending,
  downloadFailed,
  onRegenerate,
  regeneratePending,
}: {
  jobTitle: string;
  company: string | null;
  createdAt: string;
  templateName: string | null;
  assessment: Assessment | undefined;
  onCopy: () => void;
  copyLabel: string;
  onDownload: () => void;
  downloadPending: boolean;
  downloadFailed: boolean;
  onRegenerate: () => void;
  regeneratePending: boolean;
}) {
  return (
    <Card className="!p-6 sm:!p-8">
      <div className="flex flex-wrap items-start justify-between gap-6">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-wide text-ember-soft">Result</p>
          <div className="mt-2 flex flex-wrap items-center gap-3">
            <h1 className="text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
              {jobTitle}
              {company ? <span className="text-ink-faint"> — {company}</span> : null}
            </h1>
            {assessment && (
              <span
                className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ${bandStyle[assessment.readinessBand] ?? 'bg-surface-2 text-ink-faint'}`}
              >
                {bandLabel[assessment.readinessBand] ?? assessment.readinessBand}
              </span>
            )}
          </div>
          <p className="mt-2 text-sm text-ink-faint">
            Generated {new Date(createdAt).toLocaleString()}
            {templateName ? ` · ${templateName} template` : ''}
          </p>
        </div>

        <div className="flex w-full flex-wrap gap-3 sm:w-auto sm:shrink-0">
          <button
            type="button"
            onClick={onCopy}
            className="flex items-center gap-2 rounded-full border border-border-strong px-4 py-2.5 text-sm font-medium text-ink-muted transition-colors hover:border-ink-muted hover:text-ink"
          >
            <CopyIcon className="h-4 w-4" />
            {copyLabel}
          </button>
          <button
            type="button"
            onClick={onDownload}
            disabled={downloadPending}
            className="flex items-center gap-2 rounded-full bg-linear-to-r from-ember to-rose px-5 py-2.5 text-sm font-semibold text-void transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <DownloadIcon className="h-4 w-4" />
            {downloadPending ? 'Preparing…' : 'Download Resume PDF'}
          </button>
          <button
            type="button"
            onClick={onRegenerate}
            disabled={regeneratePending}
            className="flex items-center gap-2 rounded-full border border-border-strong px-4 py-2.5 text-sm font-medium text-ink-muted transition-colors hover:border-ink-muted hover:text-ink disabled:cursor-not-allowed disabled:opacity-60"
          >
            <RefreshIcon className={`h-4 w-4 ${regeneratePending ? 'animate-spin' : ''}`} />
            Regenerate
          </button>
        </div>
      </div>

      {downloadFailed && (
        <div className="mt-5 flex items-center gap-2.5 rounded-xl border border-border bg-surface-2/50 px-4 py-2.5 text-xs text-ink-muted">
          <LockIcon className="h-4 w-4 shrink-0 text-ink-faint" />
          <span>
            <span className="font-medium text-ink">PDF unavailable for this older generation.</span>{' '}
            Regenerate to get the PDF.
          </span>
        </div>
      )}
    </Card>
  );
}
