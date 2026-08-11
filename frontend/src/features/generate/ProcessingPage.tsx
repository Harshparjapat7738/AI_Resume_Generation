import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import {
  attachResume,
  createApplication,
  generateCoverLetter,
  generateEmail,
  recordOutputFailure,
} from '@/services/applicationApi';
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
 *
 * `type=ALL` ("Generate All") creates one `Application` and generates all three outputs
 * against that same `applicationId` — resume (via resume-service, then attached), cover
 * letter and email (both via application-service, already `ALL`-aware). Each step is wrapped
 * independently: one failing is recorded on the application (`recordOutputFailure`) and does
 * not stop the other two from being attempted, and this page still navigates to the combined
 * result page afterward — that page is what shows "Resume ✓ | Cover Letter ✓ | Email ✕" and
 * offers a per-output retry, reading persisted state that survives a refresh. The three run
 * sequentially, not in parallel: `Application` uses optimistic locking (`@Version`), and each
 * step reads-modifies-saves the same document, so concurrent saves would race.
 */
export function ProcessingPage() {
  const { jdId = '' } = useParams<{ jdId: string }>();
  const [searchParams] = useSearchParams();
  const templateId = searchParams.get('templateId');
  const generationType = searchParams.get('type') ?? 'RESUME_ONLY';
  const isEmailOnly = generationType === 'EMAIL_ONLY';
  const isCoverLetterOnly = generationType === 'COVER_LETTER_ONLY';
  const isAll = generationType === 'ALL';
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

  const runAll = async () => {
    // A failure creating the Application itself is a hard stop (same as every other type) —
    // there is nothing to attach a per-output failure to yet.
    const application = await createApplication(jdId, 'ALL', templateId ?? undefined);

    try {
      const resume = await generateResume(jdId, templateId ?? undefined);
      await attachResume(application.id, resume.id);
      try {
        await assessResume(resume.id);
      } catch {
        // Non-fatal, same as the RESUME_ONLY path above.
      }
    } catch (err) {
      await recordOutputFailure(application.id, 'resume', messageOf(err)).catch(() => {
        // Best-effort: if even recording the failure fails (e.g. the network just died),
        // the result page's own fetch will simply show this output as not-yet-generated
        // rather than failed — never throw a second error over the first.
      });
    }

    try {
      await generateCoverLetter(application.id);
    } catch (err) {
      await recordOutputFailure(application.id, 'coverLetter', messageOf(err)).catch(() => {});
    }

    try {
      await generateEmail(application.id);
    } catch (err) {
      await recordOutputFailure(application.id, 'email', messageOf(err)).catch(() => {});
    }

    // Always land on the combined result page, whatever the mix of success/failure above —
    // it's the one place that shows exactly which output failed and offers a retry.
    navigate(`/results/all/${application.id}`, { replace: true });
  };

  const run = async () => {
    setError(null);
    setPending(true);
    try {
      await (isEmailOnly ? runEmail() : isCoverLetterOnly ? runCoverLetter() : isAll ? runAll() : runResume());
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
      navigate(`/generate/template/${jdId}${isAll ? '?type=ALL' : ''}`, { replace: true });
      return;
    }
    startedRef.current = true;
    run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jdId, templateId, isEmailOnly, isCoverLetterOnly, isAll]);

  return (
    <GenerateLayout
      activeStep={skipsTemplate ? 3 : 4}
      steps={isEmailOnly ? EMAIL_STEPS : isCoverLetterOnly ? COVER_LETTER_STEPS : undefined}
      title={
        isEmailOnly
          ? 'Generating your email'
          : isCoverLetterOnly
            ? 'Generating your cover letter'
            : isAll
              ? 'Generating your application'
              : 'Generating your resume'
      }
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
                : isAll
                  ? 'Generating your resume, cover letter and email one after another, then running ATS and ' +
                    "job-fit analysis. This usually takes 20–40 seconds — a single output failing won't stop the others."
                  : "Matching your evidence to this job's requirements, writing grounded content, then " +
                    'running ATS and job-fit analysis. This usually takes 10–20 seconds.'}
          </p>
        </div>
      )}
    </GenerateLayout>
  );
}

function messageOf(err: unknown): string {
  return err instanceof Error && err.message ? err.message : 'Generation failed.';
}
