import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { addSkill } from '@/services/profileApi';
import type { AlignmentItem } from '../utils/skillsAlignment';

/**
 * The popup shown right after "Confirm this is correct" — replaces dumping the full
 * requirements/skills-alignment breakdown straight onto the page (still there underneath, for
 * anyone who wants the detail) with a quick two-phase focused flow: a loading state while
 * analysis runs, then — once it lands — only the genuinely *missing* skills, as one-tap chips.
 *
 * "Add" here means a bare `Skill` entry (`addSkill({ name })`, no forced experience/project
 * link) — that's still a real, non-fabricated fact: it's the *user* self-declaring "I have
 * this," the same self-attestation typing it into the Profile page's own Skills form would be,
 * not the AI inventing anything. It shows up as a PARTIAL match afterward (a named skill with no
 * structured evidence yet) rather than a false MATCHED — accurate, not overclaimed. Anyone who
 * wants to link it to real experience/project evidence still can, via the richer "Add to
 * profile" flow already on the page's Skills Alignment section (`AddSkillToProfileModal`) —
 * this popup doesn't replace that, it's just the fast path for a plain self-declared skill.
 */
export function ConfirmAnalysisModal({
  status,
  missingItems,
  onContinue,
}: {
  status: 'loading' | 'ready';
  missingItems: AlignmentItem[];
  onContinue: () => void;
}) {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const addSelectedMutation = useMutation({
    mutationFn: async () => {
      const keywords = missingItems
        .filter((item) => selected.has(item.requirementId) && item.keyword)
        .map((item) => item.keyword as string);
      for (const keyword of keywords) {
        // eslint-disable-next-line no-await-in-loop -- sequential on purpose: profile-service
        // applies one edit at a time, and these are a handful of skills at most.
        await addSkill({ name: keyword });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] });
      onContinue();
    },
  });

  const toggle = (requirementId: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(requirementId)) {
        next.delete(requirementId);
      } else {
        next.add(requirementId);
      }
      return next;
    });
  };

  const handleContinue = () => {
    if (selected.size === 0) {
      onContinue();
      return;
    }
    addSelectedMutation.mutate();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-void/80 backdrop-blur-sm animate-toast-in" />
      <div className="animate-card-in relative w-full max-w-md rounded-2xl border border-border bg-surface p-8 text-center shadow-2xl">
        {status === 'loading' ? (
          <>
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-surface-2">
              <span
                className="h-8 w-8 animate-spin rounded-full border-2 border-ember-soft border-t-transparent"
                aria-hidden="true"
              />
            </div>
            <h2 className="mt-5 text-lg font-semibold text-ink">Identifying skill gaps</h2>
            <p className="mt-2 text-sm text-ink-muted">
              Comparing this job's requirements against your profile to find what's missing, then grading your match.
            </p>
          </>
        ) : missingItems.length === 0 ? (
          <>
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-mint/10 text-2xl text-mint">
              ✓
            </div>
            <h2 className="mt-5 text-lg font-semibold text-ink">You're a strong match</h2>
            <p className="mt-2 text-sm text-ink-muted">
              No missing skills found for this role — the full breakdown is on the page below.
            </p>
            <Button className="mt-6 w-full" onClick={onContinue}>
              Continue
            </Button>
          </>
        ) : (
          <>
            <h2 className="text-lg font-semibold text-ink">Add missing skills</h2>
            <p className="mt-2 text-sm text-ink-muted">
              These are mentioned in the job description but aren't on your profile yet.
            </p>

            <p className="mt-5 text-left text-xs font-medium uppercase tracking-wide text-ink-faint">
              Select the ones you have
            </p>
            <div className="mt-2.5 flex flex-wrap gap-2 text-left">
              {missingItems.map((item) => {
                const isSelected = selected.has(item.requirementId);
                return (
                  <button
                    key={item.requirementId}
                    type="button"
                    onClick={() => toggle(item.requirementId)}
                    aria-pressed={isSelected}
                    className={`inline-flex items-center gap-1.5 rounded-full border px-3.5 py-2 text-sm font-medium transition-colors ${
                      isSelected
                        ? 'border-mint bg-mint/10 text-mint'
                        : 'border-border-strong text-ink-muted hover:border-ink-muted hover:text-ink'
                    }`}
                  >
                    <span aria-hidden="true">{isSelected ? '✓' : '+'}</span>
                    {item.keyword ?? item.text}
                  </button>
                );
              })}
            </div>

            {addSelectedMutation.isError && (
              <div className="mt-4 text-left">
                <ErrorBanner error={addSelectedMutation.error} />
              </div>
            )}

            <Button className="mt-6 w-full" loading={addSelectedMutation.isPending} onClick={handleContinue}>
              Continue
            </Button>
            <button
              type="button"
              onClick={onContinue}
              disabled={addSelectedMutation.isPending}
              className="mt-3 text-xs font-medium text-ink-faint transition-colors hover:text-ink disabled:opacity-50"
            >
              Skip for now
            </button>
          </>
        )}
      </div>
    </div>
  );
}
