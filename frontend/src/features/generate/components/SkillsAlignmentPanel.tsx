import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { AddSkillToProfileModal } from '@/features/results/components/AddSkillToProfileModal';
import type { ProfileResponse } from '@/services/profileApi';
import { REQUIREMENT_TYPE_LABELS, type AlignmentItem, type AlignmentStatus } from '../utils/skillsAlignment';

const STATUS_META: Record<AlignmentStatus, { glyph: string; label: string; className: string }> = {
  MATCHED: { glyph: '✓', label: 'You have this', className: 'text-mint' },
  PARTIAL: { glyph: '◐', label: 'Partial match', className: 'text-ember-soft' },
  MISSING: { glyph: '✕', label: 'Missing', className: 'text-rose' },
};

function evidenceLabel(evidenceId: string, profile: ProfileResponse | undefined): string {
  if (!profile) return evidenceId;
  const experience = profile.experiences.find((e) => e.evidenceId === evidenceId);
  if (experience) return [experience.title, experience.company].filter(Boolean).join(' — ') || evidenceId;
  const project = profile.projects.find((p) => p.evidenceId === evidenceId);
  if (project) return project.name || evidenceId;
  return evidenceId;
}

function AlignmentRow({
  item,
  profile,
  onAddToProfile,
}: {
  item: AlignmentItem;
  profile: ProfileResponse | undefined;
  onAddToProfile: (item: AlignmentItem) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const meta = STATUS_META[item.status];
  const hasEvidence = item.evidenceIds.length > 0;

  return (
    <li className="rounded-xl border border-border bg-void px-4 py-3 transition-colors hover:border-border-strong">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-2.5">
          <span className={`mt-0.5 shrink-0 text-sm font-semibold ${meta.className}`} aria-hidden="true">
            {meta.glyph}
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm text-ink" title={item.text}>
              {item.text}
            </p>
            <p className={`mt-0.5 text-xs ${meta.className}`}>{meta.label}</p>
          </div>
        </div>
        <span className="shrink-0 rounded-full bg-surface-2 px-2 py-0.5 text-[11px] text-ink-faint">
          {REQUIREMENT_TYPE_LABELS[item.type] ?? item.type}
        </span>
      </div>

      {item.status !== 'MISSING' && (
        <div className="mt-2 pl-[26px]">
          {hasEvidence ? (
            <button
              type="button"
              onClick={() => setExpanded((v) => !v)}
              className="text-xs font-medium text-ember-soft transition-colors hover:text-ember"
            >
              {expanded ? 'Hide evidence ▲' : 'View evidence ▼'}
            </button>
          ) : (
            item.status === 'PARTIAL' &&
            item.keyword && (
              <button
                type="button"
                onClick={() => onAddToProfile(item)}
                className="text-xs font-medium text-ember-soft transition-colors hover:text-ember"
              >
                Add evidence
              </button>
            )
          )}
          {expanded && hasEvidence && (
            <ul className="mt-2 space-y-1">
              {item.evidenceIds.map((id) => (
                <li key={id} className="flex items-center gap-1.5 text-xs text-ink-muted">
                  <span className="text-mint">✓</span>
                  {evidenceLabel(id, profile)}
                  <span className="text-ink-faint">({id})</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </li>
  );
}

/**
 * "Your skills alignment" (redesign spec &sect;9-11) — a wide, responsive multi-column grid
 * grading every matchable JD requirement against the profile via `computeSkillsAlignment`.
 * Evidence traceability and "Add to profile" both reuse existing, already-shipped machinery
 * (`findKeywordEvidence` / `AddSkillToProfileModal`) rather than reimplementing either.
 */
export function SkillsAlignmentPanel({
  items,
  profile,
}: {
  items: AlignmentItem[];
  profile: ProfileResponse | undefined;
}) {
  const queryClient = useQueryClient();
  const [modalItem, setModalItem] = useState<AlignmentItem | null>(null);

  const matched = items.filter((i) => i.status === 'MATCHED').length;
  const partial = items.filter((i) => i.status === 'PARTIAL').length;
  const missing = items.filter((i) => i.status === 'MISSING' && i.keyword);

  return (
    <div className="rounded-2xl border border-border bg-surface p-6 sm:p-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-base font-semibold text-ink">Your skills alignment</h2>
          <p className="mt-1 text-xs text-ink-faint">
            {matched} matched · {partial} partial · {missing.length} missing, computed from your profile
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-4 text-xs text-ink-faint">
          <span className="flex items-center gap-1.5">
            <span className="text-mint">✓</span> You have this
          </span>
          <span className="flex items-center gap-1.5">
            <span className="text-ember-soft">◐</span> Partial match
          </span>
          <span className="flex items-center gap-1.5">
            <span className="text-rose">✕</span> Missing
          </span>
        </div>
      </div>

      {items.length === 0 ? (
        <p className="mt-5 text-sm text-ink-faint">
          This posting didn't include any requirements specific enough to grade against your profile.
        </p>
      ) : (
        <ul className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {items.map((item) => (
            <AlignmentRow key={item.requirementId} item={item} profile={profile} onAddToProfile={setModalItem} />
          ))}
        </ul>
      )}

      {missing.length > 0 && (
        <div className="mt-6 border-t border-border pt-5">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Missing</p>
          <div className="mt-2.5 flex flex-wrap gap-2">
            {missing.map((item) => (
              <button
                key={item.requirementId}
                type="button"
                onClick={() => setModalItem(item)}
                className="inline-flex items-center gap-1.5 rounded-full border border-rose/25 bg-rose/10 px-3 py-1.5 text-xs font-medium text-rose transition-colors hover:border-rose/40"
              >
                + Add {item.keyword}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="mt-6 flex justify-center border-t border-border pt-6">
        <Link to="/profile">
          <span className="inline-flex items-center gap-2 rounded-full border-2 border-transparent px-6 py-3 text-sm font-semibold text-ink [background:linear-gradient(var(--color-surface),var(--color-surface))_padding-box,linear-gradient(90deg,var(--color-ember-soft),var(--color-rose))_border-box] transition-[filter] hover:brightness-110">
            Edit skills & experience
          </span>
        </Link>
      </div>

      {modalItem && profile && modalItem.keyword && (
        <AddSkillToProfileModal
          keyword={modalItem.keyword}
          profile={profile}
          alreadyHasSkill={modalItem.hasSkill}
          onClose={() => setModalItem(null)}
          onSaved={() => {
            queryClient.invalidateQueries({ queryKey: ['profile'] });
            setModalItem(null);
          }}
        />
      )}
    </div>
  );
}
