import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { describeError } from '@/components/ui/ErrorBanner';
import { showToast } from '@/components/ui/toast';
import { DashboardSidebar } from '@/features/dashboard/components/DashboardSidebar';
import { PlusCircleIcon } from '@/features/dashboard/icons';
import { optimizeForJd, patchOptimizationWithLatestEvidence, type JdOptimization } from '@/services/jdApi';
import { addSkill } from '@/services/profileApi';
import { useSession } from '@/services/session';
import { ContentGenerationFailure } from './components/ContentGenerationFailure';
import { GenerationProgress } from './components/GenerationProgress';
import { ReviewHeader } from './components/ReviewHeader';

const CHIP_TONE_STYLES = {
  neutral: 'bg-surface-2 text-ink-muted',
  good: 'bg-mint/10 text-mint',
  bad: 'bg-rose/10 text-rose',
} as const;

function Chip({ label, tone }: { label: string; tone: keyof typeof CHIP_TONE_STYLES }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ${CHIP_TONE_STYLES[tone]}`}>
      {label}
    </span>
  );
}

/**
 * A keyword the profile doesn't yet evidence — a skill gap. Clicking it adds a skill by that
 * exact name to the user's profile (a real fact the user is asserting about themselves, not the
 * AI inventing anything) and re-runs the optimization so the page reflects it immediately.
 * `disabled` covers both "this exact chip is mid-add" (spinner) and "some other chip is mid-add"
 * — adds are serialized one at a time, since each one costs a real Groq call and Groq's
 * per-minute token budget only reliably allows about one call at a time.
 */
function GapChip({
  label,
  tone,
  adding,
  disabled,
  onAdd,
}: {
  label: string;
  tone: 'bad' | 'neutral';
  adding: boolean;
  disabled: boolean;
  onAdd: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onAdd}
      disabled={disabled}
      title={`Add "${label}" to your profile and re-check this role against it`}
      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium transition-colors enabled:hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-60 ${CHIP_TONE_STYLES[tone]}`}
    >
      {label}
      {adding ? (
        <span
          className="h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent"
          aria-hidden="true"
        />
      ) : (
        <PlusCircleIcon className="h-3.5 w-3.5" />
      )}
    </button>
  );
}

/**
 * The "Identify Skill Gaps" step: runs JD analysis + optimization (jd-service, ADR-033/ADR-037 —
 * no confirm step) as soon as the JD exists, then shows every required/preferred keyword the
 * profile can't back as a clickable gap. Sits between JD entry and output-type selection so the
 * gaps are visible, and closeable, before the user commits to what to generate — the same
 * optimization this step computes is exactly what output-type selection and generation reuse
 * afterward (no second AI workflow, `optimizeForJd(id, refresh=false)` just re-reads the
 * persisted result if nothing changed since).
 */
export function GenerationSkillGapPage() {
  const { jdId = '' } = useParams<{ jdId: string }>();
  const { data: user } = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const startedRef = useRef(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [optimization, setOptimization] = useState<JdOptimization | null>(null);
  const [addingTerm, setAddingTerm] = useState<string | null>(null);

  const runOptimize = async (refresh: boolean) => {
    setLoading(true);
    setError(null);
    try {
      const result = await optimizeForJd(jdId, refresh);
      setOptimization(result);
      queryClient.setQueryData(['jd-optimization', jdId], result);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (startedRef.current || !jdId) return;
    startedRef.current = true;
    void runOptimize(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jdId]);

  /**
   * Adding one skill and re-checking is a local, deterministic patch (ADR-038) — zero Groq
   * calls — not a full re-adjudication. `runOptimize(true)` (a real Groq call) stays available
   * as an explicit "Regenerate" action for a user who wants a fresh, fully-judged result.
   */
  const addSkillAndReoptimize = async (term: string) => {
    if (addingTerm) return;
    setAddingTerm(term);
    try {
      const updatedProfile = await addSkill({ name: term });
      queryClient.setQueryData(['profile'], updatedProfile);
      const result = await patchOptimizationWithLatestEvidence(jdId);
      setOptimization(result);
      queryClient.setQueryData(['jd-optimization', jdId], result);
      showToast(`Added "${term}" to your profile — re-checked against this role.`);
    } catch (err) {
      showToast(describeError(err));
    } finally {
      setAddingTerm(null);
    }
  };

  if (!user) return null;
  const displayName = user.displayName?.trim() || user.email;

  const data = optimization?.optimisation;
  const required = (data?.keywords ?? []).filter((k) => k.priority === 'REQUIRED');
  const preferred = (data?.keywords ?? []).filter((k) => k.priority === 'PREFERRED');
  const missingCount =
    required.filter((k) => k.evidenceIds.length === 0).length +
    preferred.filter((k) => k.evidenceIds.length === 0).length;

  return (
    <div className="flex min-h-screen bg-void">
      <DashboardSidebar userName={displayName} userEmail={user.email} />

      <div className="flex min-w-0 flex-1 flex-col">
        <ReviewHeader
          title="Generate application"
          backLabel="Back to job description"
          backTo="/generate/job"
          showSaveDraft={false}
        />

        <main className="min-w-0 flex-1 px-5 py-7 pl-16 sm:px-7 lg:px-10 lg:py-9 lg:pl-10">
          <div className="mx-auto max-w-[1680px]">
            <GenerationProgress activeStep={1} />

            <h1 className="mt-6 text-2xl font-semibold tracking-tight text-ink sm:text-[28px]">
              Skill gaps for this role
            </h1>
            <p className="mt-1.5 max-w-2xl text-sm text-ink-muted">
              We compared this job description against your verified profile. Anything shown below
              without a green background isn't backed by your profile yet — click it to add it as
              a real skill and instantly re-check this role against it.
            </p>

            {loading && !optimization && (
              <div className="mt-10 flex flex-col items-center gap-4 rounded-2xl border border-border bg-surface px-6 py-16 text-center">
                <span
                  className="h-10 w-10 animate-spin rounded-full border-2 border-ember-soft border-t-transparent"
                  aria-hidden="true"
                />
                <p className="text-sm text-ink-muted">
                  Analyzing this job description against your verified profile. This usually takes
                  10–20 seconds.
                </p>
              </div>
            )}

            {error !== null && !optimization && (
              <div className="mt-10 space-y-4">
                <ContentGenerationFailure error={error} outputLabel="skill gap analysis" />
                <Button onClick={() => runOptimize(false)} loading={loading}>
                  Try again
                </Button>
              </div>
            )}

            {optimization && data && (
              <div className="mt-8 space-y-6">
                <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
                  <Card>
                    <h2 className="text-sm font-semibold text-ink">Required skills &amp; keywords</h2>
                    {required.length === 0 ? (
                      <p className="mt-2 text-sm text-ink-faint">None marked required for this role.</p>
                    ) : (
                      <div className="mt-3 flex flex-wrap gap-2">
                        {required.map((k) =>
                          k.evidenceIds.length > 0 ? (
                            <Chip key={k.term} label={k.term} tone="good" />
                          ) : (
                            <GapChip
                              key={k.term}
                              label={k.term}
                              tone="bad"
                              adding={addingTerm === k.term}
                              disabled={addingTerm !== null}
                              onAdd={() => addSkillAndReoptimize(k.term)}
                            />
                          ),
                        )}
                      </div>
                    )}
                  </Card>

                  <Card>
                    <h2 className="text-sm font-semibold text-ink">Preferred skills &amp; keywords</h2>
                    {preferred.length === 0 ? (
                      <p className="mt-2 text-sm text-ink-faint">None marked preferred for this role.</p>
                    ) : (
                      <div className="mt-3 flex flex-wrap gap-2">
                        {preferred.map((k) =>
                          k.evidenceIds.length > 0 ? (
                            <Chip key={k.term} label={k.term} tone="good" />
                          ) : (
                            <GapChip
                              key={k.term}
                              label={k.term}
                              tone="neutral"
                              adding={addingTerm === k.term}
                              disabled={addingTerm !== null}
                              onAdd={() => addSkillAndReoptimize(k.term)}
                            />
                          ),
                        )}
                      </div>
                    )}
                  </Card>
                </div>

                {data.derived && (
                  <div className="flex flex-col items-start gap-2 rounded-2xl border border-border bg-surface-2 p-4 text-sm sm:flex-row sm:items-center sm:justify-between">
                    <p className="text-ink-muted">
                      Updated locally from the skills you added — not re-checked by AI yet.
                    </p>
                    <Button variant="secondary" onClick={() => runOptimize(true)} loading={loading}>
                      Regenerate full check
                    </Button>
                  </div>
                )}

                <div className="flex flex-col items-start gap-3 rounded-2xl border border-border bg-surface p-6 sm:flex-row sm:items-center sm:justify-between">
                  <p className="text-sm text-ink-muted">
                    {missingCount === 0
                      ? 'Nothing missing — your profile covers every keyword for this role.'
                      : `${missingCount} keyword${missingCount === 1 ? '' : 's'} not yet backed by your profile.`}
                  </p>
                  <Button variant="primary" onClick={() => navigate(`/generate/output/${jdId}`)}>
                    Continue
                  </Button>
                </div>
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
