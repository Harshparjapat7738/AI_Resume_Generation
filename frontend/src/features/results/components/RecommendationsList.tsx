import type { Recommendation } from '@/services/assessmentApi';

const severityDot: Record<string, string> = {
  HIGH: 'bg-rose',
  MEDIUM: 'bg-ember-soft',
  LOW: 'bg-ink-faint',
};

export function RecommendationsList({ recommendations }: { recommendations: Recommendation[] }) {
  if (recommendations.length === 0) return null;

  return (
    <div className="rounded-2xl border border-border bg-surface p-6">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Recommendations</p>
      <ul className="mt-3 space-y-3">
        {recommendations.map((rec, i) => (
          <li key={i} className="flex items-start gap-2.5 text-sm text-ink-muted">
            <span className={`mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ${severityDot[rec.severity] ?? 'bg-ink-faint'}`} />
            {rec.message}
          </li>
        ))}
      </ul>
    </div>
  );
}
