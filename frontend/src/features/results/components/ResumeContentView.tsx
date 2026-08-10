import type { ResumeContent } from '@/services/resumeApi';

function EvidenceBadges({ evidenceIds }: { evidenceIds: string[] }) {
  if (evidenceIds.length === 0) return null;
  return (
    <span className="ml-2 inline-flex gap-1 align-middle">
      {evidenceIds.map((id) => (
        <span key={id} className="rounded-full bg-surface-2 px-1.5 py-0.5 text-[10px] text-ember-soft">
          {id}
        </span>
      ))}
    </span>
  );
}

export function ResumeContentView({ content }: { content: ResumeContent }) {
  return (
    <div className="space-y-6">
      {content.summary && (
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Summary</p>
          <p className="mt-2 text-sm leading-relaxed text-ink">
            {content.summary.text}
            <EvidenceBadges evidenceIds={content.summary.evidenceIds} />
          </p>
        </div>
      )}

      {content.experienceBullets && content.experienceBullets.length > 0 && (
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Experience</p>
          <div className="mt-2 space-y-4">
            {content.experienceBullets.map((group) => (
              <div key={group.evidenceId}>
                <p className="text-xs text-ink-faint">{group.evidenceId}</p>
                <ul className="mt-1 list-inside list-disc space-y-1.5 text-sm leading-relaxed text-ink">
                  {group.bullets.map((bullet, i) => (
                    <li key={i}>
                      {bullet.text}
                      <EvidenceBadges evidenceIds={bullet.evidenceIds} />
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/** Plain-text rendering for the copy-to-clipboard action — no HTML, no markup. */
export function contentAsPlainText(content: ResumeContent): string {
  const lines: string[] = [];
  if (content.summary) {
    lines.push(content.summary.text, '');
  }
  for (const group of content.experienceBullets ?? []) {
    for (const bullet of group.bullets) {
      lines.push(`• ${bullet.text}`);
    }
    lines.push('');
  }
  return lines.join('\n').trim();
}
