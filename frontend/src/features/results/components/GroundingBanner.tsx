import type { GroundingReport } from '@/services/resumeApi';

export function GroundingBanner({ grounding }: { grounding: GroundingReport }) {
  if (grounding.passed) {
    return (
      <div className="flex items-center gap-3 rounded-xl border border-mint/30 bg-mint/10 px-4 py-3 text-sm text-ink">
        <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-mint/20 text-mint">
          ✓
        </span>
        <span>
          Grounding passed — every statement traces to an evidence ID.{' '}
          <span className="text-ink-faint">({grounding.checkedStatements} statements checked)</span>
        </span>
      </div>
    );
  }
  return (
    <div className="rounded-xl border border-rose/30 bg-rose/10 px-4 py-3 text-sm text-ink">
      <p>
        {grounding.violations.length} statement{grounding.violations.length === 1 ? '' : 's'} failed grounding and
        were removed rather than shown unverified.
      </p>
      <ul className="mt-2 space-y-1 text-xs text-ink-muted">
        {grounding.violations.map((v, i) => (
          <li key={i}>
            {v.rule} — {v.location}
          </li>
        ))}
      </ul>
    </div>
  );
}
