import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { createApplication, generateCoverLetter, generateEmail } from '@/services/applicationApi';
import { assessResume } from '@/services/assessmentApi';
import { generateResume } from '@/services/resumeApi';
import { COVER_LETTER_STEPS, EMAIL_STEPS, GenerateLayout } from './GenerateLayout';

/**
 * generateResume + assessResume are two synchronous backend calls (three Groq requests
 * inside the first — see ARCHITECTURE_DECISIONS.md ADR-013 — plus deterministic Java
 * scoring for the second, no LLM involved). There's no real per-stage progress signal from
 * either, so this is an honest indeterminate wait, not a fake staged checklist.
 *
 * `templateId` travels here as a query param (set by TemplatePage) rather than component
 * state, so it survives a reload of this page — resume-service persists it on the generation
 * and every derived resume version (ARCHITECTURE_DECISIONS.md ADR-016).
 *
 * `type=EMAIL_ONLY` / `type=COVER_LETTER_ONLY` (set by OutputTypePage, carried the same way)
 * each take an entirely separate path: create the `Application` aggregate, then generate the
 * corresponding artifact — see ARCHITECTURE_DECISIONS.md ADR-019 (email) / ADR-020 (cover
 * letter). The `RESUME_ONLY` path below is otherwise unchanged.
 */
export function ProcessingPage() {
  const { jdId = '' } = useParams<{ jdId: string }>();
  const [searchParams] = useSearchParams();
  const templateId = searchParams.get('templateId');
  const generationType = searchParams.get('type') ?? 'RESUME_ONLY';
  const isEmailOnly = generationType === 'EMAIL_ONLY';
  const isCoverLetterOnly = generationType === 'COVER_LETTER_ONLY';
  const skipsTemplate = isEmailOnly || isCoverLetterOnly;
  const navigate = useNavigate();
  const startedRef = useRef(false);
  const [error, setError] = useState<unknown>(null);
  const [pending, setPending] = useState(false);

  const runResume = async () => {
    const resume = await generateResume(jdId, templateId ?? undefined);
    try {
      await assessResume(resume.id);
    } catch {
      // Assessment is a real backend call but non-fatal to this flow — the result page
      // shows the resume regardless and offers to retry scoring if it's missing.
    }
    navigate(`/results/${resume.id}`, { replace: true });
  };

  const runEmail = async () => {
    const application = await createApplication(jdId, 'EMAIL_ONLY');
    await generateEmail(application.id);
    navigate(`/results/email/${application.id}`, { replace: true });
  };

  const runCoverLetter = async () => {
    const application = await createApplication(jdId, 'COVER_LETTER_ONLY');
    await generateCoverLetter(application.id);
    navigate(`/results/cover-letter/${application.id}`, { replace: true });
  };

  const run = async () => {
    setError(null);
    setPending(true);
    try {
      await (isEmailOnly ? runEmail() : isCoverLetterOnly ? runCoverLetter() : runResume());
    } catch (err) {
      setError(err);
    } finally {
      setPending(false);
    }
  };

  useEffect(() => {
    if (startedRef.current || !jdId) return;
    if (!skipsTemplate && !templateId) {
      // No template was selected — send the user back rather than silently generating with
      // whatever the backend defaults to. Email/cover-letter generation has no template step
      // to return to.
      navigate(`/generate/template/${jdId}`, { replace: true });
      return;
    }
    startedRef.current = true;
    run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jdId, templateId, isEmailOnly, isCoverLetterOnly]);

  return (
    <GenerateLayout
      activeStep={skipsTemplate ? 3 : 4}
      steps={isEmailOnly ? EMAIL_STEPS : isCoverLetterOnly ? COVER_LETTER_STEPS : undefined}
      title={isEmailOnly ? 'Generating your email' : isCoverLetterOnly ? 'Generating your cover letter' : 'Generating your resume'}
      subtitle="Grounded in your evidence only — nothing here is invented."
    >
      {error !== null ? (
        <div className="space-y-4">
          <ErrorBanner error={error} />
          <div className="flex gap-3">
            <Button onClick={run} loading={pending}>
              Try again
            </Button>
            <Link
              to={`/generate/review/${jdId}?type=${generationType}`}
              className="inline-flex items-center text-sm text-ink-muted hover:text-ink"
            >
              Back to review
            </Link>
          </div>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-4 rounded-2xl border border-border bg-surface px-6 py-16 text-center">
          <span
            className="h-10 w-10 animate-spin rounded-full border-2 border-ember-soft border-t-transparent"
            aria-hidden="true"
          />
          <p className="text-sm text-ink-muted">
            {isEmailOnly
              ? 'Writing a grounded application email from your evidence. This usually takes a few seconds.'
              : isCoverLetterOnly
                ? "Matching your evidence to this job's requirements and writing a grounded cover letter. This usually takes a few seconds."
                : "Matching your evidence to this job's requirements, writing grounded content, then " +
                  'running ATS and job-fit analysis. This usually takes 10–20 seconds.'}
          </p>
        </div>
      )}
    </GenerateLayout>
  );
}
