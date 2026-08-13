import { Button } from '@/components/ui/Button';
import { BadgeCheckIcon } from '@/features/dashboard/icons';

export type RegenerationStage = 'idle' | 'generating' | 'assessing' | 'done' | 'failed';

const STAGE_LABEL: Record<Exclude<RegenerationStage, 'idle' | 'done' | 'failed'>, string> = {
  generating: 'Generating resume & validating grounding…',
  assessing: 'Running ATS & JD analysis…',
};

/**
 * The visible pipeline-state strip for "Regenerate Resume" (redesign spec &sect;13/14) —
 * shown right below the hero. Only reports stages that correspond to a real, observable async
 * call this app actually makes (`generateResume`, then `assessResume`) — never fabricated
 * progress steps like a separate "rendering template" tick, since PDF rendering here stays a
 * distinct, user-triggered action (the existing "Download Resume PDF" button), not part of
 * this automatic chain. A failure always shows the real backend-supplied reason and a retry
 * that reruns the exact same regeneration — never a silently "successful" result.
 */
export function RegenerationStatus({
  stage,
  error,
  onRetry,
}: {
  stage: RegenerationStage;
  error: string | null;
  onRetry: () => void;
}) {
  if (stage === 'idle') return null;

  if (stage === 'failed') {
    return (
      <div className="rounded-2xl border border-rose/30 bg-rose/10 px-5 py-4">
        <p className="text-sm font-semibold text-ink">Resume generation failed.</p>
        {error && (
          <p className="mt-1 text-sm text-ink-muted">
            <span className="font-medium text-ink">Reason:</span> {error}
          </p>
        )}
        <Button variant="secondary" className="mt-3 !px-4 !py-2 !text-sm" onClick={onRetry}>
          Retry Generation
        </Button>
      </div>
    );
  }

  if (stage === 'done') {
    return (
      <div className="flex items-center gap-2 rounded-2xl border border-mint/30 bg-mint/10 px-5 py-3 text-sm text-mint">
        <BadgeCheckIcon className="h-4 w-4 shrink-0" />
        Resume regenerated successfully — scores and missing keywords below are up to date.
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2.5 rounded-2xl border border-border bg-surface px-5 py-3 text-sm text-ink-muted">
      <span className="h-3.5 w-3.5 shrink-0 animate-spin rounded-full border-2 border-current border-t-transparent" aria-hidden="true" />
      {STAGE_LABEL[stage]}
    </div>
  );
}
