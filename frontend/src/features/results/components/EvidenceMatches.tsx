import type { EvidenceMatch } from '@/services/resumeApi';

const strengthColor: Record<string, string> = {
  STRONG: 'text-mint',
  PARTIAL: 'text-ember-soft',
  NONE: 'text-ink-faint',
};

export function EvidenceMatches({ matches }: { matches: EvidenceMatch[] }) {
  const matched = matches.filter((m) => m.matchStrength !== 'NONE');
  if (matched.length === 0) return null;

  return (
    <div className="overflow-x-auto rounded-xl border border-border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-ink-faint">
            <th className="px-4 py-2.5 font-medium">Requirement</th>
            <th className="px-4 py-2.5 font-medium">Evidence</th>
            <th className="px-4 py-2.5 font-medium">Strength</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {matched.map((match) => (
            <tr key={match.requirementId}>
              <td className="px-4 py-2.5 text-ink-muted">{match.requirementId}</td>
              <td className="px-4 py-2.5 text-ink-muted">{match.evidenceIds.join(', ')}</td>
              <td className={`px-4 py-2.5 font-medium ${strengthColor[match.matchStrength] ?? 'text-ink-faint'}`}>
                {match.matchStrength}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
