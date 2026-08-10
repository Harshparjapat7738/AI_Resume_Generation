export function ScoreCard({ label, score, outOf = 100 }: { label: string; score: number; outOf?: number }) {
  const pct = Math.max(0, Math.min(100, (score / outOf) * 100));
  const color = pct >= 75 ? 'from-mint to-mint' : pct >= 50 ? 'from-ember-soft to-rose' : 'from-rose to-rose';

  return (
    <div className="rounded-2xl border border-border bg-surface p-5">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-ink">
        {Math.round(score)}
        <span className="text-sm font-normal text-ink-faint"> / {outOf}</span>
      </p>
      <div className="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
        <div className={`h-full rounded-full bg-linear-to-r ${color}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
