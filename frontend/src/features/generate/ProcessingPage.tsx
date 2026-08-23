import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { createApplication, generateEmail } from '@/services/applicationApi';
import { optimizeForJd } from '@/services/jdApi';
import { ContentGenerationFailure } from './components/ContentGenerationFailure';
import { GenerateLayout } from './GenerateLayout';

/**
 * Runs the one backend call the chosen output needs, then redirects to its result page.
 *
 * <p>Two working flows today (ADR-033/ADR-036): JD-optimized resume (`POST /api/jd/{id}/optimize`
 * — cheap/idempotent here since the skill-gap step already computed it; the result page's own
 * button drives the actual resume-render call) and application email (create the `Application`,
 * then generate its content). Cover Letter and "All" have no generation logic behind them yet —
 * OutputTypePage never sends either type here, so this page never has to handle them.
 *
 * <p>There's no per-stage progress signal from either call, so the optimization checklist marks
 * the JD and profile as already done — they genuinely are, resolved in the skill-gap step — and
 * shows only the (typically instant, already-cached) re-check as in flight. Nothing here fakes a
 * timeline.
 */
export function ProcessingPage() {
  const { jdId = '' } = useParams<{ jdId: string }>();
  const [searchParams] = useSearchParams();
  const generationType = searchParams.get('type') ?? 'RESUME_ONLY';
  const isEmailOnly = generationType === 'EMAIL_ONLY';
  const isOptimization = !isEmailOnly;
  const navigate = useNavigate();
  const startedRef = useRef(false);
  const [error, setError] = useState<unknown>(null);
  const [pending, setPending] = useState(false);
  // Real stages, not a fake timeline: the JD and profile are genuinely resolved server-side
  // before the single AI call, so by the time this page is running both are already done and
  // the only step still in flight is the optimization itself.
  const [optimizeStage, setOptimizeStage] = useState<'idle' | 'running'>('idle');


  const runOptimization = async () => {
    setOptimizeStage('running');
    const result = await optimizeForJd(jdId);
    navigate(`/results/optimization/${result.jobDescriptionId}`, { replace: true });
  };

  const runEmail = async () => {
    const application = await createApplication(jdId, 'EMAIL_ONLY');
    await generateEmail(application.id);
    navigate(`/results/email/${application.id}`, { replace: true });
  };



  const run = async () => {
    setError(null);
    setPending(true);
    try {
      await (isEmailOnly ? runEmail() : runOptimization());
    } catch (err) {
      setError(err);
    } finally {
      setPending(false);
    }
  };

  useEffect(() => {
    if (startedRef.current || !jdId) return;
    startedRef.current = true;
    run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jdId, isEmailOnly]);


  const outputLabel = isOptimization
    ? 'JD optimization'
    : 'email';

  return (
    <GenerateLayout
      activeStep={3}
      title={isOptimization ? 'Generating Your JD Optimization' : 'Generating Your Email'}
      subtitle="Grounded in your evidence only — nothing here is invented."
    >
      {error !== null ? (
        <div className="space-y-4">
          {(
            // Every failure reachable here is a *content*-generation failure: this page only
            // ever calls the content endpoints, and each of them persists nothing unless both
            // AI stages succeeded. So there is never validated content to fall back to at this
            // point — the document-generation fallback (which does hand over the finished JSON)
            // lives on the result pages, where rendering is actually attempted.
            <ContentGenerationFailure error={error} outputLabel={outputLabel} />
          )}
          <div className="flex gap-3">
            <Button onClick={run} loading={pending}>
              Try again
            </Button>
            <Link
              to={`/generate/output/${jdId}`}
              className="inline-flex items-center text-sm text-ink-muted hover:text-ink"
            >
              Back to output type
            </Link>
          </div>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-4 rounded-2xl border border-border bg-surface px-6 py-16 text-center">
          <span
            className="h-10 w-10 animate-spin rounded-full border-2 border-ember-soft border-t-transparent"
            aria-hidden="true"
          />
          {isOptimization && (
            <ul className="mb-2 space-y-1.5 text-sm">
              <li className="text-mint"><span aria-hidden="true">✓ </span>Job description analyzed</li>
              <li className="text-mint"><span aria-hidden="true">✓ </span>Verified profile loaded</li>
              <li className="text-ember-soft">
                <span aria-hidden="true">● </span>
                {optimizeStage === 'running' ? 'Building JD optimization…' : 'Building JD optimization'}
              </li>
            </ul>
          )}
          <p className="text-sm text-ink-muted">
            {isOptimization
              ? 'Matching your verified evidence to this job’s requirements and identifying keywords and gaps. This usually takes 10–20 seconds.'
              : 'Writing a grounded application email from your evidence. This usually takes a few seconds.'}
          </p>
        </div>
      )}
    </GenerateLayout>
  );
}

