import { Link } from 'react-router-dom';
import { ApiError } from '@/services/apiClient';

/** Which pipeline stage the failure stopped at, as far as the error actually tells us. Never
 *  guessed beyond what the backend reports: anything unrecognised is attributed to the content
 *  stage only because that is the one stage this page is certain to have attempted. */
type Stage = 'jd' | 'profile' | 'content';

interface Diagnosis {
  stage: Stage;
  /** What went wrong, in the user's terms — not the raw server string where we can do better. */
  detail: string;
  /** Where the user can actually fix it, when the cause is fixable by them. */
  action?: { label: string; to: string };
  /** Whether retrying the identical request could plausibly succeed. A missing profile section
   *  or an unextractable JD will fail identically until the user changes something. */
  retryable: boolean;
}

/** The API's own error envelope, verbatim — shown alongside the friendly copy so the exact
 *  code/message/correlationId the server returned is always visible, not just inferred from it. */
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

function diagnose(error: unknown, outputLabel: string): Diagnosis {
  const code = error instanceof ApiError ? error.body.code : null;
  const message = error instanceof ApiError ? error.body.message : null;

  if (code === 'VALIDATION_ERROR' && message) {
    // resume-service raises these three before spending an AI request, each with a message
    // that already names the real cause — surface it, and point at the screen that fixes it.
    if (/experience|profile/i.test(message)) {
      return {
        stage: 'profile',
        detail: message,
        action: { label: 'Go to your profile', to: '/profile' },
        retryable: false,
      };
    }
    if (/requirement/i.test(message)) {
      return { stage: 'jd', detail: message, retryable: false };
    }
    if (/matches|evidence/i.test(message)) {
      return {
        stage: 'content',
        detail: message,
        action: { label: 'Add matching experience', to: '/profile' },
        retryable: false,
      };
    }
    return { stage: 'content', detail: message, retryable: false };
  }

  if (code === 'UPSTREAM_UNAVAILABLE') {
    return {
      stage: 'profile',
      detail: 'We couldn’t reach one of the services holding your profile or job description. This is usually temporary.',
      retryable: true,
    };
  }

  if (code === 'AI_GENERATION_FAILED') {
    return {
      // Show the backend's own message rather than a hand-written override — the backend
      // already decides what's safe to say (never a stack trace, exception class, or raw
      // provider body — see CLAUDE.md), so its wording is the most accurate diagnostic text
      // available without inventing anything.
      stage: 'content',
      detail: message ?? `The AI provider couldn’t complete your ${outputLabel}.`,
      retryable: true,
    };
  }

  return {
    stage: 'content',
    detail:
      error instanceof ApiError && error.body.message
        ? error.body.message
        : 'Something went wrong before your content was produced.',
    retryable: true,
  };
}

const STAGE_ORDER: Stage[] = ['jd', 'profile', 'content'];

/**
 * The content-generation failure state for the generation wizard.
 *
 * <p>Distinct from `DocumentFallbackPanel` on the result pages, and deliberately so: that panel
 * exists for the case where content generation *succeeded* and only PDF/DOCX rendering failed,
 * so it can hand over finished structured content. Here, nothing reached validation and nothing
 * was persisted — `ResumeGenerationService.generate` saves the `ResumeVersion` only after both
 * AI stages return, so every failure path this component renders means there is genuinely no
 * content to show. Inventing a "your data is ready" section here would be a lie.
 *
 * <p>What it does instead: name the stage that stopped things, say whether anything was saved,
 * and — when the cause is something the user can actually fix (an empty profile section, an
 * unconfirmed JD) — send them to the screen that fixes it rather than looping them through a
 * retry that will fail identically.
 */
export function ContentGenerationFailure({ error, outputLabel }: { error: unknown; outputLabel: string }) {
  const { stage, detail, action, retryable } = diagnose(error, outputLabel);
  const raw = rawDetails(error);
  const failedIndex = STAGE_ORDER.indexOf(stage);

  const stageLabels: Record<Stage, string> = {
    jd: 'Job description analyzed',
    profile: 'Candidate information processed',
    content: `${outputLabel.charAt(0).toUpperCase()}${outputLabel.slice(1)} content generated`,
  };

  return (
    <div className="rounded-2xl border border-ember/30 bg-ember/5 p-5 sm:p-6">
      <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm">
        {STAGE_ORDER.map((s, index) => (
          <span
            key={s}
            className={`inline-flex items-center gap-1.5 ${
              index < failedIndex ? 'text-mint' : index === failedIndex ? 'text-ember-soft' : 'text-ink-faint'
            }`}
          >
            <span aria-hidden="true">{index < failedIndex ? '✓' : index === failedIndex ? '✗' : '·'}</span>
            {stageLabels[s]}
          </span>
        ))}
        <span className="inline-flex items-center gap-1.5 text-ink-faint">
          <span aria-hidden="true">·</span>
          Content validated
        </span>
      </div>

      <h2 className="mt-5 text-lg font-semibold text-ink">We couldn’t generate your {outputLabel} content.</h2>
      <p className="mt-2 text-sm leading-relaxed text-ink-muted">{detail}</p>
      <p className="mt-2 text-sm leading-relaxed text-ink-muted">
        Nothing was saved, so none of your profile or job description data was changed
        {retryable ? ' — retrying starts cleanly from the beginning.' : '.'}
      </p>

      {action && (
        <Link
          to={action.to}
          className="mt-4 inline-flex items-center text-sm font-medium text-ember-soft hover:text-ink"
        >
          {action.label} →
        </Link>
      )}

      {raw && (
        <details className="mt-4 text-xs text-ink-faint">
          <summary className="cursor-pointer select-none hover:text-ink-muted">
            Error details
          </summary>
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
