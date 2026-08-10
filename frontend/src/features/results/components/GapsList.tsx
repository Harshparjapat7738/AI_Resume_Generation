import type { Gap } from '@/services/resumeApi';

export function GapsList({ gaps }: { gaps: Gap[] }) {
  if (gaps.length === 0) {
    return null;
  }
  return (
    <div className="rounded-xl border border-border bg-void p-4">
      <p className="text-sm font-medium text-ink">What's missing</p>
      <p className="mt-1 text-xs text-ink-faint">
        No evidence in your profile supports these requirements — reported as gaps, never invented.
      </p>
      <ul className="mt-3 space-y-2">
        {gaps.map((gap) => (
          <li key={gap.requirementId} className="flex items-start justify-between gap-3 text-sm">
            <span className="text-ink-muted">{gap.text}</span>
            <span className="shrink-0 rounded-full bg-surface-2 px-2 py-0.5 text-xs text-ink-faint">{gap.type}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
