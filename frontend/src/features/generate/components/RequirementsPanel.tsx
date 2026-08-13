import type { JdAnalysis } from '@/services/jdApi';
import { REQUIREMENT_GROUPS } from '../utils/skillsAlignment';

const CHIP_STYLE: Record<string, string> = {
  HARD_REQUIRED: 'border-border-strong bg-surface-2 text-ink',
  PREFERRED: 'border-rose/25 bg-rose/10 text-rose',
  SKILLS: 'border-border-strong bg-surface-2 text-ink',
  EDUCATION: 'border-border-strong bg-surface-2 text-ink-muted',
  RESPONSIBILITY: 'border-border-strong bg-surface-2 text-ink-muted',
};

/** Right column of the review grid (redesign spec &sect;7-8) — the JD's own extracted
 *  requirements, grouped into a handful of labeled chip rows instead of 40+ full-width vertical
 *  cards. Uses exactly `analysis.requirements`/`analysis.title`/`analysis.company`/
 *  `analysis.seniority` — nothing here is invented client-side. */
export function RequirementsPanel({ analysis }: { analysis: JdAnalysis }) {
  const groups = REQUIREMENT_GROUPS.map((group) => ({
    ...group,
    items: analysis.requirements.filter((req) => group.types.includes(req.type)),
  })).filter((group) => group.items.length > 0);

  return (
    <div className="flex h-full flex-col rounded-2xl border border-border bg-surface p-6 sm:p-7">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-base font-semibold text-ink">
          {analysis.title ?? 'Untitled role'}
          {analysis.company ? <span className="font-normal text-ink-muted"> at {analysis.company}</span> : null}
        </h2>
        {analysis.seniority && (
          <span className="shrink-0 rounded-full border border-border-strong px-2.5 py-1 text-xs text-ink-muted">
            {analysis.seniority}
          </span>
        )}
      </div>

      {groups.length === 0 ? (
        <p className="mt-4 text-sm text-ink-faint">No structured requirements were extracted from this posting.</p>
      ) : (
        <div className="mt-5 flex-1 space-y-5 overflow-y-auto pr-1 sm:max-h-[26rem]">
          {groups.map((group) => (
            <div key={group.id}>
              <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">{group.label}</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {group.items.map((req) => (
                  <span
                    key={req.requirementId}
                    title={req.text}
                    className={`rounded-full border px-3 py-1.5 text-xs font-medium ${CHIP_STYLE[group.id] ?? CHIP_STYLE.SKILLS}`}
                  >
                    {req.text.length > 60 ? `${req.text.slice(0, 57)}…` : req.text}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
