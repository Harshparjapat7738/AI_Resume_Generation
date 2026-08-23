import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { TextArea } from '@/components/ui/TextArea';
import { TextField } from '@/components/ui/TextField';
import { DashboardSidebar } from '@/features/dashboard/components/DashboardSidebar';
import { ApiError } from '@/services/apiClient';
import { fetchJdFromUrl, submitJd } from '@/services/jdApi';
import { useSession } from '@/services/session';
import { GenerationCta } from './components/GenerationCta';
import { GenerationProgress } from './components/GenerationProgress';
import { ReviewHeader } from './components/ReviewHeader';
import { clearJdDraft, readJdDraft, writeJdDraft } from './utils/jdDraft';

const pasteSchema = z.object({
  jobDescriptionText: z
    .string()
    .trim()
    .min(50, 'Paste the full job description (at least 50 characters)')
    .max(60_000, "That's too long — trim it to the core description"),
});
type PasteFormValues = z.infer<typeof pasteSchema>;

const urlSchema = z.object({
  url: z.string().trim().url('Enter a valid URL, including https://').max(2000),
});
type UrlFormValues = z.infer<typeof urlSchema>;

/**
 * The "Job Description" step, now the very first step of the wizard (no Confirm/Review step,
 * and output type is no longer chosen up front — see OutputTypePage, now the *third* step,
 * reached after skill gaps). This page no longer knows or needs a generation type at all: the
 * same JD feeds skill-gap identification, then whichever output the user picks afterward.
 *
 * The "Continue" action stays a single button with that exact, literal label — every e2e spec
 * that drives this page asserts `getByRole('button', { name: 'Continue', exact: true })`.
 */
export function GenerationJobDescriptionPage() {
  const { data: user } = useSession();
  const navigate = useNavigate();

  // Read once, synchronously, before the forms below initialize from it — restores whatever the
  // user had typed/pasted/fetched if they navigated away from this step and came back before
  // submitting (see utils/jdDraft.ts). Lazy initializer so this only ever runs on first mount.
  const [draft] = useState(() => readJdDraft());
  const [tab, setTab] = useState<'paste' | 'url'>(draft.tab);
  const [submitError, setSubmitError] = useState<unknown>(null);
  const [urlError, setUrlError] = useState<unknown>(null);

  const pasteForm = useForm<PasteFormValues>({
    resolver: zodResolver(pasteSchema),
    defaultValues: { jobDescriptionText: draft.jobDescriptionText },
  });
  const urlForm = useForm<UrlFormValues>({
    resolver: zodResolver(urlSchema),
    defaultValues: { url: draft.url },
  });

  const length = pasteForm.watch('jobDescriptionText')?.length ?? 0;

  // Persist every keystroke to the draft — cheap (one small JSON blob) and means a stray
  // navigation away from this step never has to be "the one time" that loses what was typed.
  useEffect(() => {
    const subscription = pasteForm.watch((values) => {
      writeJdDraft({ jobDescriptionText: values.jobDescriptionText ?? '' });
    });
    return () => subscription.unsubscribe();
  }, [pasteForm]);

  useEffect(() => {
    const subscription = urlForm.watch((values) => {
      writeJdDraft({ url: values.url ?? '' });
    });
    return () => subscription.unsubscribe();
  }, [urlForm]);

  const changeTab = (next: 'paste' | 'url') => {
    setTab(next);
    writeJdDraft({ tab: next });
  };

  const onSubmitPaste = async (values: PasteFormValues) => {
    setSubmitError(null);
    try {
      const jd = await submitJd(values.jobDescriptionText);
      // The job description now lives server-side under jd.id — the skill-gap step reads it
      // from there, so the local draft has done its job and won't leak into a later, unrelated
      // visit to this step.
      clearJdDraft();
      navigate(`/generate/skill-gap/${jd.id}`);
    } catch (error) {
      setSubmitError(error);
    }
  };

  const onSubmitUrl = async (values: UrlFormValues) => {
    setUrlError(null);
    try {
      const jd = await fetchJdFromUrl(values.url);
      clearJdDraft();
      navigate(`/generate/skill-gap/${jd.id}`);
    } catch (error) {
      setUrlError(error);
    }
  };

  const switchToPaste = () => {
    setUrlError(null);
    changeTab('paste');
  };

  // "Unable to extract" covers both a fetch that failed outright (JD_VALIDATION_ERROR) and
  // one the SSRF guard rejected (JD_URL_BLOCKED) — the user doesn't need to know which; the
  // fallback is the same either way.
  const urlErrorMessage =
    urlError instanceof ApiError && (urlError.body.code === 'JD_VALIDATION_ERROR' || urlError.body.code === 'JD_URL_BLOCKED')
      ? 'Unable to extract this job description from this URL.'
      : null;

  if (!user) {
    return <FullScreenSpinner label="Loading…" />;
  }

  const displayName = user.displayName?.trim() || user.email;
  return (
    <div className="flex min-h-screen bg-void">
      <DashboardSidebar userName={displayName} userEmail={user.email} />

      <div className="flex min-w-0 flex-1 flex-col">
        <ReviewHeader
          title="Generate application"
          backLabel="Back to dashboard"
          backTo="/dashboard"
          showSaveDraft={false}
        />

        <main className="min-w-0 flex-1 px-5 py-7 pl-16 sm:px-7 lg:px-10 lg:py-9 lg:pl-10">
          <div className="mx-auto max-w-[1680px]">
            <GenerationProgress activeStep={0} />

            <div className="mt-6 flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-semibold tracking-tight text-ink sm:text-[28px]">
                Add the Job Description
              </h1>
            </div>
            <p className="mt-1.5 text-sm text-ink-muted">
              We'll identify skill gaps against your verified profile, then you can build a
              JD-optimized resume, cover letter or application email from it.
            </p>

            <div className="mt-8 mx-auto max-w-3xl">
              <div className="rounded-2xl border border-border bg-surface p-6 sm:p-8">
                <h2 className="text-base font-semibold text-ink">Job description</h2>
                <p className="mt-1 text-xs text-ink-faint">Paste the complete job posting, or import it from a URL.</p>

                <div className="mt-5 inline-flex rounded-full border border-border bg-void p-1 text-sm">
                  <button
                    type="button"
                    onClick={() => changeTab('paste')}
                    className={`rounded-full px-4 py-1.5 transition-colors ${
                      tab === 'paste' ? 'bg-ink text-void' : 'text-ink-muted hover:text-ink'
                    }`}
                  >
                    Paste Job Description
                  </button>
                  <button
                    type="button"
                    onClick={() => changeTab('url')}
                    className={`rounded-full px-4 py-1.5 transition-colors ${
                      tab === 'url' ? 'bg-ink text-void' : 'text-ink-muted hover:text-ink'
                    }`}
                  >
                    Job URL
                  </button>
                </div>

                {tab === 'url' ? (
                  <div className="mt-5 space-y-4">
                    <form className="space-y-4" onSubmit={urlForm.handleSubmit(onSubmitUrl)} noValidate>
                      <TextField
                        label="Job posting URL"
                        type="url"
                        placeholder="https://company.com/careers/software-engineer"
                        error={urlForm.formState.errors.url?.message}
                        {...urlForm.register('url')}
                      />
                      <p className="text-xs text-ink-faint">
                        Works best on company career pages and ATS platforms (Greenhouse, Lever, Workday and
                        similar) that publish structured job data. LinkedIn and Indeed often block automated
                        fetches — we don't attempt to bypass that.
                      </p>
                      <div className="flex justify-end">
                        <Button type="submit" loading={urlForm.formState.isSubmitting} className="!px-5 !py-3">
                          Fetch job description
                        </Button>
                      </div>
                    </form>

                    {urlErrorMessage && (
                      <div className="rounded-xl border border-rose/30 bg-rose/10 p-4 text-sm">
                        <p className="text-ink">{urlErrorMessage}</p>
                        <button
                          type="button"
                          onClick={switchToPaste}
                          className="mt-3 font-medium text-ink underline underline-offset-2"
                        >
                          Paste Job Description Instead
                        </button>
                      </div>
                    )}
                    {urlError !== null && !urlErrorMessage && <ErrorBanner error={urlError} />}
                  </div>
                ) : (
                  <form className="mt-5 space-y-3" onSubmit={pasteForm.handleSubmit(onSubmitPaste)} noValidate>
                    {submitError !== null ? <ErrorBanner error={submitError} /> : null}
                    <TextArea
                      label="Job description"
                      rows={16}
                      className="!min-h-[420px] sm:!min-h-[460px]"
                      placeholder="Paste the complete job posting here…"
                      error={pasteForm.formState.errors.jobDescriptionText?.message}
                      {...pasteForm.register('jobDescriptionText')}
                    />
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <ValidationHint length={length} />
                      <div className="flex items-center gap-3">
                        <span className="text-xs text-ink-faint">{length.toLocaleString()} / 60,000 characters</span>
                        {length > 0 && (
                          <button
                            type="button"
                            onClick={() => {
                              pasteForm.setValue('jobDescriptionText', '', { shouldValidate: false });
                              writeJdDraft({ jobDescriptionText: '' });
                            }}
                            className="text-xs font-medium text-ink-muted transition-colors hover:text-ink"
                          >
                            Clear
                          </button>
                        )}
                      </div>
                    </div>
                  </form>
                )}
              </div>
            </div>

            {tab === 'paste' && (
              <div className="mt-6">
                <GenerationCta
                  heading="Ready to continue?"
                  description="Next we'll identify skill gaps against your verified profile before you choose what to generate."
                  ctaLabel="Continue"
                  loading={pasteForm.formState.isSubmitting}
                  onGenerate={() => {
                    void pasteForm.handleSubmit(onSubmitPaste)();
                  }}
                />
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}

/** Live, reactive validation feedback (redesign spec &sect;12) alongside the textarea's own
 *  submit-time zod error — mirrors the exact same 50-character minimum `pasteSchema` enforces,
 *  so it's never out of sync with what "Continue" will actually accept. */
function ValidationHint({ length }: { length: number }) {
  if (length === 0) {
    return <p className="text-xs text-ink-faint">Paste the job posting to continue.</p>;
  }
  if (length < 50) {
    return (
      <p className="flex items-center gap-1.5 text-xs text-ember-soft">
        <span aria-hidden="true">●</span>
        {50 - length} more character{50 - length === 1 ? '' : 's'} needed
      </p>
    );
  }
  return (
    <p className="flex items-center gap-1.5 text-xs text-mint">
      <span aria-hidden="true">✓</span>
      Job description looks good
    </p>
  );
}
