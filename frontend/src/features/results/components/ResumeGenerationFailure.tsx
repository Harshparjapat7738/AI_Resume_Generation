import { Button } from '@/components/ui/Button';
import { ApiError } from '@/services/apiClient';

/**
 * Shown when `generateResumePdf` fails on the result page (ADR-040).
 *
 * <p>Distinct from `ContentGenerationFailure` in the generation wizard, and deliberately so:
 * that component covers a failure where nothing was produced at all — no JD optimization, no
 * evidence match, nothing to fall back to. Here the opposite is true: by the time this page can
 * even attempt a PDF, the JD optimization already succeeded and is fully rendered above (keywords,
 * matches, missing requirements, the scores card). Only the last, optional step — turning that
 * already-validated content into a downloadable PDF — failed. So this panel never repeats "nothing
 * was saved" (something very real *was* produced), and it always points back at what is still
 * usable right now: the scores above, and the JSON/AI-prompt export this page already offers as
 * its primary deliverable (ADR-033) — the same data the PDF would have used, in a form the user
 * can paste into any AI tool or apply to their existing resume themselves.
 */
function rawDetails(error: unknown): { code: string; status: number; message: string; correlationId?: string | undefined } | null {
  if (!(error instanceof ApiError)) {
    return null;
  }
  return {
    code: error.body.code,
    status: error.status,
    message: error.body.message,
    correlationId: error.body.correlationId,
  };
}

function friendlyDetail(error: unknown): string {
  const code = error instanceof ApiError ? error.body.code : null;
  const message = error instanceof ApiError ? error.body.message : null;

  if (code === 'VALIDATION_ERROR' && message) {
    return message;
  }
  if (code === 'DOCUMENT_RENDER_FAILED') {
    return "The document renderer couldn't produce a usable PDF from your resume content this time.";
  }
  if (code === 'UPSTREAM_UNAVAILABLE') {
    return 'We couldn’t reach one of the services needed to build the PDF. This is usually temporary.';
  }
  return message ?? 'Something went wrong while building the PDF.';
}

export function ResumeGenerationFailure({
  error,
  onRetry,
  retrying,
}: {
  error: unknown;
  onRetry: () => void;
  retrying: boolean;
}) {
  const detail = friendlyDetail(error);
  const raw = rawDetails(error);

  return (
    <div className="rounded-2xl border border-ember/30 bg-ember/5 p-5">
      <h3 className="text-sm font-semibold text-ink">We couldn’t generate your resume PDF this time.</h3>
      <p className="mt-2 text-sm leading-relaxed text-ink-muted">{detail}</p>
      <p className="mt-2 text-sm leading-relaxed text-ink-muted">
        Your JD optimization and scores above are unaffected — they don't depend on this step and
        are already saved. You can retry the PDF, or continue right now with your existing resume
        or template using the Copy AI Prompt / Copy or Download JSON options above: they carry the
        exact same evidence and keyword targeting the PDF would have used.
      </p>
      <div className="mt-4">
        <Button variant="secondary" onClick={onRetry} loading={retrying}>
          Retry PDF generation
        </Button>
      </div>

      {raw && (
        <details className="mt-4 text-xs text-ink-faint">
          <summary className="cursor-pointer select-none hover:text-ink-muted">Error details</summary>
          <dl className="mt-2 space-y-1 rounded-lg border border-border bg-surface p-3 font-mono">
            <div className="flex gap-2">
              <dt className="shrink-0 text-ink-faint">status</dt>
              <dd className="break-all text-ink-muted">{raw.status}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="shrink-0 text-ink-faint">code</dt>
              <dd className="break-all text-ink-muted">{raw.code}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="shrink-0 text-ink-faint">message</dt>
              <dd className="break-all text-ink-muted">{raw.message}</dd>
            </div>
            {raw.correlationId && (
              <div className="flex gap-2">
                <dt className="shrink-0 text-ink-faint">correlationId</dt>
                <dd className="break-all text-ink-muted">{raw.correlationId}</dd>
              </div>
            )}
          </dl>
        </details>
      )}
    </div>
  );
}
