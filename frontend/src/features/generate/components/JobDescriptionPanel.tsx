import type { JdDetail } from '@/services/jdApi';

/** Left column of the review grid (redesign spec &sect;7) — the raw submitted/extracted text,
 *  scrolling inside its own fixed-height area so a long posting never stretches the whole page
 *  (spec &sect;7: "Do NOT allow the entire page to become extremely tall because of raw JD text"). */
export function JobDescriptionPanel({ jd }: { jd: JdDetail }) {
  const hasUrlPreview =
    jd.sourceType === 'URL' && (jd.title || jd.company || jd.location || jd.skillsSummary || jd.experienceSummary);

  return (
    <div className="flex h-full flex-col rounded-2xl border border-border bg-surface p-6 sm:p-7">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
          {jd.sourceType === 'URL' ? 'Extracted from URL' : 'Submitted job description'}
        </p>
      </div>
      {jd.sourceType === 'URL' && jd.sourceUrl && (
        <p className="mt-1 truncate text-xs text-ink-faint">{jd.sourceUrl}</p>
      )}

      {hasUrlPreview && (
        <dl className="mt-4 grid gap-3 border-b border-border pb-5 sm:grid-cols-2">
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

      <div className="mt-4 min-h-0 flex-1 overflow-y-auto whitespace-pre-wrap rounded-xl border border-border bg-void p-4 text-sm leading-relaxed text-ink-muted sm:max-h-[26rem]">
        {jd.rawText}
      </div>
    </div>
  );
}
