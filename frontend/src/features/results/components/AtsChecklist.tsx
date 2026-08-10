import type { AtsCheck } from '@/services/assessmentApi';

export function AtsChecklist({ checks }: { checks: AtsCheck[] }) {
  return (
    <div className="rounded-2xl border border-border bg-surface p-6">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
        ATS compatibility — detailed breakdown
      </p>
      <ul className="mt-4 space-y-4">
        {checks.map((check) => (
          <li key={check.checkId}>
            <div className="flex items-center justify-between text-sm">
              <span className="text-ink">{check.label}</span>
              <span className="text-ink-faint">
                {check.earned.toFixed(1)} / {check.weight}
              </span>
            </div>
            <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
              <div
                className="h-full rounded-full bg-linear-to-r from-ember-soft to-rose"
                style={{ width: `${check.passRatio * 100}%` }}
              />
            </div>
            <p className="mt-1 text-xs text-ink-faint">{check.detail}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}
