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

const subScores = [
  { key: 'coverage', label: 'Requirement coverage' },
  { key: 'keywordMatch', label: 'Keyword match' },
  { key: 'seniorityMatch', label: 'Seniority match' },
  { key: 'recency', label: 'Recency' },
] as const;

export function JdFitBreakdown({ assessment }: { assessment: Assessment }) {
  return (
    <div className="rounded-2xl border border-border bg-surface p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
            Estimated job description match
          </p>
          <p className="mt-1 text-2xl font-semibold text-ink">
            {Math.round(assessment.compatibilityScore * 100)}%
          </p>
        </div>
        <span
          className={`rounded-full px-3 py-1.5 text-xs font-semibold ${bandStyle[assessment.readinessBand] ?? 'bg-surface-2 text-ink-faint'}`}
        >
          {bandLabel[assessment.readinessBand] ?? assessment.readinessBand}
        </span>
      </div>

      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        {subScores.map(({ key, label }) => {
          const value = assessment[key];
          return (
            <div key={key}>
              <div className="flex items-center justify-between text-xs">
                <span className="text-ink-muted">{label}</span>
                <span className="text-ink-faint">{Math.round(value * 100)}%</span>
              </div>
              <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
                <div
                  className="h-full rounded-full bg-linear-to-r from-ember-soft to-rose"
                  style={{ width: `${value * 100}%` }}
                />
              </div>
            </div>
          );
        })}
      </div>

      {assessment.unmetHardRequirements.length > 0 && (
        <div className="mt-5 rounded-xl border border-rose/30 bg-rose/10 p-4">
          <p className="text-xs font-medium text-ink">Unmet hard requirements</p>
          <ul className="mt-2 space-y-1 text-xs text-ink-muted">
            {assessment.unmetHardRequirements.map((req) => (
              <li key={req.requirementId}>{req.text}</li>
            ))}
          </ul>
        </div>
      )}

      <p className="mt-4 text-xs text-ink-faint">
        Estimated screening compatibility, not a guarantee — every employer's actual ATS and
        reviewers differ.
      </p>
    </div>
  );
}
