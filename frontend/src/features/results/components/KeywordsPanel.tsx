export function KeywordsPanel({ matched, missing }: { matched: string[]; missing: string[] }) {
  if (matched.length === 0 && missing.length === 0) return null;

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {matched.length > 0 && (
        <div className="rounded-2xl border border-border bg-surface p-5">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Matched keywords</p>
          <ul className="mt-3 flex flex-wrap gap-2">
            {matched.map((kw) => (
              <li key={kw} className="flex items-center gap-1 rounded-full bg-mint/10 px-2.5 py-1 text-xs text-mint">
                ✓ {kw}
              </li>
            ))}
          </ul>
        </div>
      )}
      {missing.length > 0 && (
        <div className="rounded-2xl border border-border bg-surface p-5">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Missing keywords</p>
          <ul className="mt-3 flex flex-wrap gap-2">
            {missing.map((kw) => (
              <li key={kw} className="rounded-full bg-surface-2 px-2.5 py-1 text-xs text-ink-muted">
                {kw}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
