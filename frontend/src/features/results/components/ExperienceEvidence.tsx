import { useState } from 'react';
import type { ResumeContent } from '@/services/resumeApi';

const DEFAULT_VISIBLE = 2;

/** A vertical-timeline view of the same `resume.content.experienceBullets` data
 *  `ResumeContentView`'s "Experience" section already renders — this component only adds the
 *  timeline presentation and, once there are more than a couple of entries, a "View full
 *  experience" expand affordance so the dashboard grid cell doesn't grow unbounded. No new
 *  data — every evidence id and bullet here is exactly what's already on `resume.content`. */
export function ExperienceEvidence({ experienceBullets }: { experienceBullets: ResumeContent['experienceBullets'] }) {
  const [expanded, setExpanded] = useState(false);
  const groups = experienceBullets ?? [];
  if (groups.length === 0) return null;

  const visible = expanded ? groups : groups.slice(0, DEFAULT_VISIBLE);
  const hasMore = groups.length > DEFAULT_VISIBLE;

  return (
    <div className="rounded-2xl border border-border bg-surface p-6">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Experience</p>
      <ol className="mt-4 space-y-6 border-l border-border pl-5">
        {visible.map((group) => (
          <li key={group.evidenceId} className="relative">
            <span className="absolute -left-[25px] top-1 h-2.5 w-2.5 rounded-full bg-ember-soft ring-4 ring-surface" aria-hidden="true" />
            <p className="text-xs font-semibold text-ember-soft">{group.evidenceId}</p>
            <ul className="mt-2 space-y-1.5 text-sm leading-relaxed text-ink-muted">
              {group.bullets.map((bullet, i) => (
                <li key={i} className="flex gap-2">
                  <span className="text-ink-faint">•</span>
                  <span>{bullet.text}</span>
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ol>
      {hasMore && (
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="mt-4 text-xs font-medium text-ember-soft transition-colors hover:text-ember"
        >
          {expanded ? 'Show less' : `View full experience (${groups.length})`}
        </button>
      )}
    </div>
  );
}
