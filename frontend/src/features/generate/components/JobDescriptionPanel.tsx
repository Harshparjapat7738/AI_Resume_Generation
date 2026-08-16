import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { MarkdownContent } from '@/components/ui/MarkdownContent';
import type { JdDetail } from '@/services/jdApi';

const MIN_JD_LENGTH = 50;
const MAX_JD_LENGTH = 60_000;

const EditIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <path d="M16.5 4.5a2.1 2.1 0 0 1 3 3L8 19l-4 1 1-4Z" />
    <path d="m14.5 6.5 3 3" />
  </svg>
);

/**
 * The Review step's single, full-width "document review workspace" card (redesign brief) —
 * `GenerationReviewPage` owns the actual draft text/edit-mode/confirm-mutation state (it also
 * needs `hasUnsavedChanges` to gate Back/Save draft elsewhere on the page), this only renders
 * the read/edit/confirm states and reports user intent upward.
 *
 * The "Fetched from URL" / "Extracted description" / "Submitted text" labels below are exact
 * strings several e2e specs assert on (jd-url.spec.ts, template-selection.spec.ts), and
 * "Confirm this is correct" is asserted by nearly every generation-flow spec — all kept
 * unchanged through the redesign.
 */
export function JobDescriptionPanel({
  jd,
  editable,
  isEditing,
  draftText,
  onDraftChange,
  onStartEdit,
  onCancelEdit,
  onSave,
  saving = false,
  saveError,
  isConfirmed,
  onConfirm,
  confirming = false,
  confirmError,
}: {
  jd: JdDetail;
  /** Off once the JD is confirmed — see `JdService#editText`: an edit is rejected server-side
   *  past that point, since a confirmed JD's analysis is pinned to an exact version. */
  editable: boolean;
  isEditing: boolean;
  draftText: string;
  onDraftChange: (text: string) => void;
  onStartEdit: () => void;
  onCancelEdit: () => void;
  onSave: () => void;
  saving?: boolean;
  saveError?: unknown;
  isConfirmed: boolean;
  onConfirm: () => void;
  confirming?: boolean;
  confirmError?: unknown;
}) {
  const hasUrlPreview =
    jd.sourceType === 'URL' && (jd.title || jd.company || jd.location || jd.skillsSummary || jd.experienceSummary);
  const draftLength = draftText.length;
  const draftTooShort = draftLength > 0 && draftLength < MIN_JD_LENGTH;
  const draftEmpty = draftLength === 0;

  if (isEditing) {
    return (
      <div className="flex max-h-[min(70vh,44rem)] flex-col rounded-2xl border border-border-strong bg-surface p-6 sm:p-9">
        <h2 className="text-xl font-semibold text-ink sm:text-2xl">Edit Job Description</h2>
        <p className="mt-1.5 text-sm text-ink-faint">Make your changes, then save — nothing is analyzed until you do.</p>

        {saveError !== undefined && saveError !== null && (
          <div className="mt-4">
            <ErrorBanner error={saveError} />
          </div>
        )}

        <textarea
          value={draftText}
          onChange={(event) => onDraftChange(event.target.value)}
          maxLength={MAX_JD_LENGTH}
          aria-label="Job description"
          className="mt-5 min-h-0 flex-1 resize-none rounded-xl border border-border bg-void p-5 text-[15px] leading-relaxed text-ink placeholder:text-ink-faint focus:outline-none focus:ring-2 focus:ring-ember-soft/40 sm:min-h-[28rem]"
          placeholder="Paste or type the job description here…"
        />

        <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs">
          {draftEmpty ? (
            <span className="text-ink-faint">Characters: 0 — the job description can't be empty.</span>
          ) : draftTooShort ? (
            <span className="text-ember-soft">Characters: {draftLength.toLocaleString()} — at least {MIN_JD_LENGTH} needed.</span>
          ) : (
            <span className="text-ink-faint">Characters: {draftLength.toLocaleString()}</span>
          )}
        </div>

        <div className="mt-5 flex justify-end gap-3 border-t border-border pt-5">
          <Button type="button" variant="secondary" className="!px-5 !py-2.5 !text-sm" disabled={saving} onClick={onCancelEdit}>
            Cancel
          </Button>
          <Button
            type="button"
            className="!px-6 !py-2.5 !text-sm"
            loading={saving}
            disabled={saving || draftEmpty || draftTooShort}
            onClick={onSave}
          >
            Save Changes
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex max-h-[min(70vh,44rem)] flex-col rounded-2xl border border-border bg-surface">
      <div className="flex items-start justify-between gap-3 p-6 pb-0 sm:p-9 sm:pb-0">
        <h2 className="text-xl font-semibold text-ink sm:text-2xl">Submitted Job Description</h2>
        {editable && (
          <button
            type="button"
            onClick={onStartEdit}
            className="flex shrink-0 items-center gap-1.5 rounded-full border border-border-strong px-3.5 py-2 text-xs font-medium text-ink transition-colors hover:border-ink-muted"
          >
            <EditIcon className="h-3.5 w-3.5" />
            Edit JD
          </button>
        )}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-6 py-6 sm:px-9 sm:py-7">
        {jd.sourceType === 'URL' && (
          <div className="mb-5 rounded-xl border border-border bg-void/50 p-3">
            <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Fetched from URL</p>
            {jd.sourceUrl && <p className="mt-1 truncate text-xs text-ink-faint">{jd.sourceUrl}</p>}
          </div>
        )}

        {hasUrlPreview && (
          <dl className="mb-6 grid gap-3 border-b border-border pb-6 sm:grid-cols-2">
            {jd.title && (
              <div>
                <dt className="text-xs text-ink-faint">Job title</dt>
                <dd className="text-sm text-ink">{jd.title}</dd>
              </div>
            )}
            {jd.company && (
              <div>
                <dt className="text-xs text-ink-faint">Company</dt>
                <dd className="text-sm text-ink">{jd.company}</dd>
              </div>
            )}
            {jd.location && (
              <div>
                <dt className="text-xs text-ink-faint">Location</dt>
                <dd className="text-sm text-ink">{jd.location}</dd>
              </div>
            )}
            {jd.experienceSummary && (
              <div>
                <dt className="text-xs text-ink-faint">Experience</dt>
                <dd className="text-sm text-ink">{jd.experienceSummary}</dd>
              </div>
            )}
          </dl>
        )}

        <p className="mb-3 text-xs font-medium uppercase tracking-wide text-ink-faint">
          {jd.sourceType === 'URL' ? 'Extracted description' : 'Submitted text'}
        </p>
        <MarkdownContent text={jd.rawText} className="text-[15px] leading-[1.8] text-ink-muted" />
      </div>

      <div className="shrink-0 border-t border-border px-6 py-5 sm:px-9">
        {confirmError !== undefined && confirmError !== null && (
          <div className="mb-4">
            <ErrorBanner error={confirmError} />
          </div>
        )}
        {isConfirmed ? (
          <p className="flex items-center gap-2 text-sm font-medium text-mint">
            <span aria-hidden="true">✓</span> Confirmed — requirements extracted below.
          </p>
        ) : (
          <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
            <p className="text-sm text-ink-muted">
              Confirm this is the right job — once confirmed, we'll extract its requirements and grade it against
              your profile.
            </p>
            <Button
              type="button"
              className="w-full shrink-0 !px-6 !py-2.5 !text-sm sm:w-auto"
              loading={confirming}
              onClick={onConfirm}
            >
              Confirm this is correct
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
