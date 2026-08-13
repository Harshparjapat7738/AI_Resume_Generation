import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Select } from '@/components/ui/Select';
import { TextArea } from '@/components/ui/TextArea';
import { addSkill, updateExperience, updateProject, type ProfileResponse } from '@/services/profileApi';
import { addUnique, findKeywordEvidence } from '../utils/keywordEvidence';

// Same finite picklist SkillsManager.tsx (Profile page) uses — kept as its own small local
// copy rather than a shared import, matching how that convention already works elsewhere in
// this codebase (e.g. ResultHero's own local `bandStyle`/`bandLabel`).
const PROFICIENCY_LEVELS = ['Beginner', 'Intermediate', 'Advanced', 'Expert'];

const MIN_EVIDENCE_LENGTH = 10;

/**
 * "Add to Profile" flow (redesign spec &sect;2) for a single missing keyword — opened from
 * `MissingKeywordsPanel`. Never invents evidence: the user must pick a real experience/project
 * they actually used the skill in and describe it themselves, then explicitly confirm it's
 * genuine before Save is enabled. On save this:
 *   1. Appends the keyword to that experience/project's `technologies` (deduped) — the same
 *      structured field AI generation and the assessment engine already read for grounding.
 *   2. Appends the user's own description as a new bullet (experience) or description
 *      paragraph (project) — real, user-authored evidence text, not fabricated content.
 *   3. Adds a matching Skill entry only if one doesn't already exist (no duplicate skills).
 * Nothing here calls resume generation — saving only updates the profile; regenerating is a
 * separate, explicit step the caller triggers afterwards.
 */
export function AddSkillToProfileModal({
  keyword,
  profile,
  alreadyHasSkill,
  onClose,
  onSaved,
}: {
  keyword: string;
  profile: ProfileResponse;
  alreadyHasSkill: boolean;
  onClose: () => void;
  onSaved: (evidenceIds: string[]) => void;
}) {
  const queryClient = useQueryClient();
  const [targetKey, setTargetKey] = useState('');
  const [evidenceText, setEvidenceText] = useState('');
  const [proficiency, setProficiency] = useState('');
  const [confirmed, setConfirmed] = useState(false);

  const hasAnyTarget = profile.experiences.length > 0 || profile.projects.length > 0;
  const canSave = targetKey !== '' && evidenceText.trim().length >= MIN_EVIDENCE_LENGTH && confirmed;

  const saveMutation = useMutation({
    mutationFn: async (): Promise<ProfileResponse> => {
      const [type, id] = targetKey.split(':');
      let updated: ProfileResponse;
      if (type === 'exp') {
        const experience = profile.experiences.find((item) => item.evidenceId === id);
        if (!experience) throw new Error('That experience entry no longer exists — refresh and try again.');
        updated = await updateExperience(experience.evidenceId, {
          company: experience.company ?? '',
          title: experience.title ?? '',
          employmentType: experience.employmentType ?? undefined,
          start: experience.start ?? undefined,
          end: experience.end ?? undefined,
          current: experience.current,
          location: experience.location ?? undefined,
          bullets: [...experience.bullets, evidenceText.trim()],
          technologies: addUnique(experience.technologies, keyword),
          metrics: experience.metrics,
        });
      } else {
        const project = profile.projects.find((item) => item.evidenceId === id);
        if (!project) throw new Error('That project entry no longer exists — refresh and try again.');
        updated = await updateProject(project.evidenceId, {
          name: project.name ?? '',
          description: [project.description, evidenceText.trim()].filter(Boolean).join('\n\n'),
          role: project.role ?? undefined,
          technologies: addUnique(project.technologies, keyword),
          metrics: project.metrics,
          githubUrl: project.githubUrl ?? undefined,
          liveUrl: project.liveUrl ?? undefined,
          start: project.start ?? undefined,
          end: project.end ?? undefined,
        });
      }
      if (!alreadyHasSkill) {
        updated = await addSkill({ name: keyword, proficiency: proficiency || undefined });
      }
      return updated;
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(['profile'], updated);
      onSaved(findKeywordEvidence(keyword, updated).evidenceIds);
    },
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-void/70 backdrop-blur-sm animate-toast-in" onClick={onClose} />
      <div className="animate-card-in relative flex max-h-[85vh] w-full max-w-lg flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-2xl">
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink">Add skill to profile</h2>
            <p className="mt-0.5 text-xs text-ink-faint">Only add this if you genuinely have experience with it.</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="flex h-8 w-8 items-center justify-center rounded-lg text-ink-muted hover:text-ink"
          >
            ✕
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-5">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Skill</p>
            <p className="mt-1.5 inline-flex items-center rounded-full bg-surface-2 px-3 py-1 text-sm font-medium text-ink">
              {keyword}
            </p>
          </div>

          {!hasAnyTarget ? (
            <div className="mt-5 rounded-xl border border-border bg-void p-4 text-sm text-ink-muted">
              No supporting evidence found.
              <p className="mt-1 text-xs text-ink-faint">
                You don't have any experience or project entries yet, so there's nothing genuine to attach this
                skill to. Add one on your{' '}
                <Link to="/profile" className="text-ember-soft hover:text-ember">
                  profile page
                </Link>{' '}
                first, then come back.
              </p>
            </div>
          ) : (
            <div className="mt-5 space-y-4">
              <Select
                label="Related experience or project"
                value={targetKey}
                onChange={(event) => setTargetKey(event.target.value)}
              >
                <option value="">Select where you used this…</option>
                {profile.experiences.length > 0 && (
                  <optgroup label="Experience">
                    {profile.experiences.map((experience) => (
                      <option key={experience.evidenceId} value={`exp:${experience.evidenceId}`}>
                        {[experience.title, experience.company].filter(Boolean).join(' — ') || experience.evidenceId}
                      </option>
                    ))}
                  </optgroup>
                )}
                {profile.projects.length > 0 && (
                  <optgroup label="Projects">
                    {profile.projects.map((project) => (
                      <option key={project.evidenceId} value={`proj:${project.evidenceId}`}>
                        {project.name || project.evidenceId}
                      </option>
                    ))}
                  </optgroup>
                )}
              </Select>

              <TextArea
                label="Experience / evidence"
                placeholder={`Describe where/how you genuinely used ${keyword}…`}
                hint="This becomes a real, attributable line on the experience or project you selected — the AI can only draw on what you write here."
                rows={3}
                value={evidenceText}
                onChange={(event) => setEvidenceText(event.target.value)}
              />

              {!alreadyHasSkill && (
                <Select label="Proficiency (optional)" value={proficiency} onChange={(event) => setProficiency(event.target.value)}>
                  <option value="">Select proficiency</option>
                  {PROFICIENCY_LEVELS.map((level) => (
                    <option key={level} value={level}>
                      {level}
                    </option>
                  ))}
                </Select>
              )}

              <label className="flex items-start gap-2.5 text-sm text-ink-muted">
                <input
                  type="checkbox"
                  checked={confirmed}
                  onChange={(event) => setConfirmed(event.target.checked)}
                  className="mt-0.5 h-4 w-4 shrink-0 rounded border-border-strong accent-ember"
                />
                I confirm this is genuine — I actually have this experience and the description above is accurate.
              </label>

              {saveMutation.isError && <ErrorBanner error={saveMutation.error} />}
            </div>
          )}
        </div>

        <div className="flex justify-end gap-3 border-t border-border px-6 py-4">
          <Button type="button" variant="secondary" className="!px-4 !py-2 !text-sm" onClick={onClose}>
            Cancel
          </Button>
          {hasAnyTarget && (
            <Button
              type="button"
              className="!px-5 !py-2 !text-sm"
              loading={saveMutation.isPending}
              disabled={!canSave || saveMutation.isPending}
              onClick={() => saveMutation.mutate()}
            >
              Save to profile
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
