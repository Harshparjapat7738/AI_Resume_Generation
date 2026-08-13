import { useState } from 'react';
import { BadgeCheckIcon, PlusCircleIcon, RefreshIcon } from '@/features/dashboard/icons';
import type { ProfileResponse } from '@/services/profileApi';
import { findKeywordEvidence } from '../utils/keywordEvidence';
import { AddSkillToProfileModal } from './AddSkillToProfileModal';

/**
 * The "Add Missing Keywords → Update Profile → Regenerate Resume" workflow (redesign spec
 * &sect;1–5) — one row per `assessment.missingKeywords` entry, each showing whatever the
 * candidate's real profile already supports for it and the one honest next action:
 *   - no evidence anywhere → [Add to Profile] (opens the modal; nothing is fabricated)
 *   - evidence already exists (a matching skill and/or a technologies-tagged experience/
 *     project) → [Include in Resume] triggers the *existing*, unmodified regenerate flow —
 *     this never forces the keyword into the resume; it only gives the unchanged AI
 *     generation + grounding pipeline updated, genuine material to work with. Whether the
 *     regenerated resume actually uses it is still entirely up to that pipeline.
 * Deliberately a sibling of `KeywordsPanel` (which still renders the matched-keywords side on
 * this page) rather than a replacement for it — `KeywordsPanel` itself is untouched so
 * `AllResultPage` keeps working exactly as before.
 */
export function MissingKeywordsPanel({
  missingKeywords,
  profile,
  profileLoading,
  onRegenerate,
  regenerating,
}: {
  missingKeywords: string[];
  profile: ProfileResponse | undefined;
  profileLoading: boolean;
  onRegenerate: () => void;
  regenerating: boolean;
}) {
  const [modalKeyword, setModalKeyword] = useState<string | null>(null);
  // Session-local bookkeeping only — "which keywords did *this* Add-to-Profile flow just save
  // evidence for", so the row can say "Added to profile" instead of "Existing evidence" right
  // after a save. Never a source of truth: evidence status itself is always recomputed live
  // from the current profile query below.
  const [justAdded, setJustAdded] = useState<Record<string, string[]>>({});

  if (missingKeywords.length === 0) return null;

  return (
    <div className="rounded-2xl border border-border bg-surface p-6 sm:p-8">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Missing keywords</p>
      <p className="mt-1.5 text-sm text-ink-muted">
        The job description mentions these but the generated resume doesn't. Only add one if you genuinely have
        that experience — never just to raise a score.
      </p>

      {profileLoading ? (
        <p className="mt-5 text-sm text-ink-faint">Loading your profile…</p>
      ) : !profile ? (
        <p className="mt-5 text-sm text-ink-faint">Couldn't load your profile — try refreshing.</p>
      ) : (
        <ul className="mt-5 space-y-3">
          {missingKeywords.map((keyword) => {
            const evidence = findKeywordEvidence(keyword, profile);
            const hasEvidence = evidence.hasSkill || evidence.evidenceIds.length > 0;
            const addedEvidenceIds = justAdded[keyword];
            const wasJustAdded = addedEvidenceIds !== undefined;

            return (
              <li key={keyword} className="rounded-xl border border-border bg-void px-4 py-3.5 sm:px-5 sm:py-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-ink">{keyword}</p>
                    {wasJustAdded ? (
                      <p className="mt-0.5 flex items-center gap-1.5 text-xs text-mint">
                        <BadgeCheckIcon className="h-3.5 w-3.5" />
                        Added to profile{addedEvidenceIds.length > 0 ? ` — evidence: ${addedEvidenceIds.join(', ')}` : ''}
                      </p>
                    ) : evidence.evidenceIds.length > 0 ? (
                      <p className="mt-0.5 flex items-center gap-1.5 text-xs text-ink-faint">
                        <BadgeCheckIcon className="h-3.5 w-3.5 text-mint" />
                        Existing evidence: {evidence.evidenceIds.join(', ')}
                      </p>
                    ) : evidence.hasSkill ? (
                      <p className="mt-0.5 flex items-center gap-1.5 text-xs text-ink-faint">
                        <BadgeCheckIcon className="h-3.5 w-3.5 text-mint" />
                        Already in profile — not yet used in this resume
                      </p>
                    ) : (
                      <p className="mt-0.5 text-xs text-ink-faint">Missing from generated resume</p>
                    )}
                  </div>

                  {hasEvidence || wasJustAdded ? (
                    <button
                      type="button"
                      onClick={onRegenerate}
                      disabled={regenerating}
                      className="flex shrink-0 items-center gap-1.5 rounded-full border border-border-strong px-3.5 py-1.5 text-xs font-medium text-ink-muted transition-colors hover:border-ink-muted hover:text-ink disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      <RefreshIcon className={`h-3.5 w-3.5 ${regenerating ? 'animate-spin' : ''}`} />
                      Include in Resume
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setModalKeyword(keyword)}
                      className="flex shrink-0 items-center gap-1.5 rounded-full border border-border-strong px-3.5 py-1.5 text-xs font-medium text-ink-muted transition-colors hover:border-ink-muted hover:text-ink"
                    >
                      <PlusCircleIcon className="h-3.5 w-3.5" />
                      Add to Profile
                    </button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {modalKeyword && profile && (
        <AddSkillToProfileModal
          keyword={modalKeyword}
          profile={profile}
          alreadyHasSkill={findKeywordEvidence(modalKeyword, profile).hasSkill}
          onClose={() => setModalKeyword(null)}
          onSaved={(evidenceIds) => {
            setJustAdded((prev) => ({ ...prev, [modalKeyword]: evidenceIds }));
            setModalKeyword(null);
          }}
        />
      )}
    </div>
  );
}
