import { useState } from 'react';
import type { AtsCheck } from '@/services/assessmentApi';

/** `limit` is opt-in and omitted entirely by AllResultPage (its own resume tab) — every
 *  existing caller keeps showing every check with no toggle, exactly as before. Only a caller
 *  that passes it (ResultPage's redesigned dashboard layout) gets the "show N, then View all
 *  details" truncation, so a long checklist doesn't dominate a grid cell it shares with other
 *  cards. */
export function AtsChecklist({ checks, limit }: { checks: AtsCheck[]; limit?: number }) {
  const [expanded, setExpanded] = useState(false);
  const truncated = typeof limit === 'number' && !expanded && checks.length > limit;
  const visible = truncated ? checks.slice(0, limit) : checks;

  return (
    <div className="rounded-2xl border border-border bg-surface p-6">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
        ATS compatibility — detailed breakdown
      </p>
      <ul className="mt-4 space-y-4">
        {visible.map((check) => (
          <li key={check.checkId}>
            <div className="flex items-center justify-between text-sm">
              <span className="text-ink">{check.label}</span>
              <span className="text-ink-faint">
                {check.earned.toFixed(1)} / {check.weight}
              </span>
            </div>
            <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
              <div
                className="h-full rounded-full bg-linear-to-r from-ember-soft to-rose transition-[width] duration-300"
                style={{ width: `${check.passRatio * 100}%` }}
              />
            </div>
            <p className="mt-1 text-xs text-ink-faint">{check.detail}</p>
          </li>
        ))}
      </ul>
      {typeof limit === 'number' && checks.length > limit && (
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="mt-4 text-xs font-medium text-ember-soft transition-colors hover:text-ember"
        >
          {expanded ? 'Show less' : `View all details (${checks.length})`}
        </button>
      )}
    </div>
  );
}
