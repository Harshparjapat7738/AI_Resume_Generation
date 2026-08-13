import { useState } from 'react';
import type { EvidenceMatch } from '@/services/resumeApi';

const strengthStyle: Record<string, string> = {
  STRONG: 'bg-mint/10 text-mint',
  PARTIAL: 'bg-ember-soft/10 text-ember-soft',
  NONE: 'bg-surface-2 text-ink-faint',
};

/** The wide "Requirement Coverage" card (point 14 of the redesign spec) — a self-contained
 *  sibling of the existing `EvidenceMatches` table (deliberately not reusing/editing that
 *  component: `AllResultPage` renders it too, and this redesign must not change that page).
 *  "View job description analysis" is a real, working toggle over the exact same
 *  `resume.evidenceMatches` data already on the page: collapsed shows only requirements this
 *  resume actually matched (what `EvidenceMatches` already showed); expanded reveals the
 *  unmatched ones too — real information the collapsed view was hiding, not a mock action. */
export function RequirementCoverage({ matches }: { matches: EvidenceMatch[] }) {
  const [showAll, setShowAll] = useState(false);
  if (matches.length === 0) return null;

  const matched = matches.filter((m) => m.matchStrength !== 'NONE');
  const visible = showAll ? matches : matched;
  if (visible.length === 0) return null;

  return (
    <div className="rounded-2xl border border-border bg-surface p-6 sm:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Requirement coverage</p>
        <button
          type="button"
          onClick={() => setShowAll((v) => !v)}
          className="text-xs font-medium text-ember-soft transition-colors hover:text-ember"
        >
          {showAll ? 'Show matched only' : 'View job description analysis'}
        </button>
      </div>

      {/* Desktop/tablet: table. Mobile: the same rows as stacked cards — see the sm:hidden /
          hidden sm:block split below, satisfying point 14's "convert rows into stacked cards"
          without a horizontally-scrolling table on a narrow screen. */}
      <div className="mt-4 hidden overflow-x-auto rounded-xl border border-border sm:block">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-ink-faint">
              <th className="px-4 py-2.5 font-medium">Requirement</th>
              <th className="px-4 py-2.5 font-medium">Evidence</th>
              <th className="px-4 py-2.5 font-medium">Strength</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {visible.map((match) => (
              <tr key={match.requirementId}>
                <td className="px-4 py-2.5 text-ink-muted">{match.requirementId}</td>
                <td className="px-4 py-2.5 text-ink-muted">{match.evidenceIds.join(', ') || '—'}</td>
                <td className="px-4 py-2.5">
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${strengthStyle[match.matchStrength] ?? 'bg-surface-2 text-ink-faint'}`}>
                    {match.matchStrength}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ul className="mt-4 space-y-2.5 sm:hidden">
        {visible.map((match) => (
          <li key={match.requirementId} className="rounded-xl border border-border px-4 py-3">
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm font-medium text-ink">{match.requirementId}</span>
              <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${strengthStyle[match.matchStrength] ?? 'bg-surface-2 text-ink-faint'}`}>
                {match.matchStrength}
              </span>
            </div>
            <p className="mt-1 text-xs text-ink-faint">{match.evidenceIds.join(', ') || 'No evidence'}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}
