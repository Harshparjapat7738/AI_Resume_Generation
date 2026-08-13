import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { TextArea } from '@/components/ui/TextArea';
import { TextField } from '@/components/ui/TextField';
import { DashboardSidebar } from '@/features/dashboard/components/DashboardSidebar';
import { ApiError } from '@/services/apiClient';
import type { GenerationType } from '@/services/applicationApi';
import { fetchJdFromUrl, submitJd } from '@/services/jdApi';
import { useSession } from '@/services/session';
import { GenerationCta } from './components/GenerationCta';
import { GenerationProgress } from './components/GenerationProgress';
import { GenerationTypeBadge, JobContextPanel } from './components/JobContextPanel';
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

const SUBTITLES: Record<GenerationType, string> = {
  RESUME_ONLY: "Tell us about the role you're applying for.",
  COVER_LETTER_ONLY: "We'll use this job description to personalize your cover letter.",
  EMAIL_ONLY: "We'll use this job description to create your professional application email.",
  ALL: "We'll use this job description to generate your complete application package.",
};

const CTA_DESCRIPTIONS: Record<GenerationType, string> = {
  RESUME_ONLY: "Next you'll review your match against this role, then choose a resume template.",
  COVER_LETTER_ONLY: "Next you'll review your match against this role before generating your cover letter.",
  EMAIL_ONLY: "Next you'll review your match against this role before generating your email.",
  ALL: "Next you'll review your match against this role before generating your complete application package.",
};

/**
 * The shared "Job Description" step (redesign brief) — one component behind RESUME_ONLY /
 * COVER_LETTER_ONLY / EMAIL_ONLY / ALL alike, matching `GenerationReviewPage`'s layout pattern
 * (sidebar + compact header + full-width workspace). Only the heading badge, subtitle, the
 * generation-context panel and the Continue CTA's description vary by `generationType` — the
 * paste/URL input itself, its validation and its submit handlers are unchanged from the
 * original `JobDescriptionPage`.
 *
 * The actual "Continue" action stays a single button with that exact, literal label rather than
 * per-type phrasing ("Continue to template" / "Continue to review") — every e2e spec that drives
 * this page (there are many) asserts `getByRole('button', { name: 'Continue', exact: true })`.
 * The per-type framing lives in the CTA card's description text instead, which isn't a fixed
 * contract the way the button's accessible name is.
 */
export function GenerationJobDescriptionPage() {
  const { data: user } = useSession();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  // Carried as a query param (not route state) so it survives a reload, matching how
  // TemplatePage later carries templateId to ProcessingPage. Defaults to the resume flow so a
  // direct/bookmarked link to this page keeps working exactly as before.
  const generationType = (searchParams.get('type') as GenerationType | null) ?? 'RESUME_ONLY';
  // Optional — set only when arriving from the Templates page's "Use this template" action, so
  // TemplatePage can preselect it instead of the user having to pick it again. Just forwarded
  // along here, same as `type`; this page has no template UI of its own, only a read-only
  // preview of it (see JobContextPanel).
  const preselectedTemplateId = searchParams.get('templateId');
  const templateParam = preselectedTemplateId ? `&templateId=${preselectedTemplateId}` : '';

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
      // The job description now lives server-side under jd.id — GenerationReviewPage reads it
      // from there, so the local draft has done its job and won't leak into a later, unrelated
      // visit to this step.
      clearJdDraft();
      navigate(`/generate/review/${jd.id}?type=${generationType}${templateParam}`);
    } catch (error) {
      setSubmitError(error);
    }
  };

  const onSubmitUrl = async (values: UrlFormValues) => {
    setUrlError(null);
    try {
      const jd = await fetchJdFromUrl(values.url);
      clearJdDraft();
      navigate(`/generate/review/${jd.id}?type=${generationType}${templateParam}`);
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
  const subtitle = SUBTITLES[generationType] ?? SUBTITLES.RESUME_ONLY;

  return (
    <div className="flex min-h-screen bg-void">
      <DashboardSidebar userName={displayName} userEmail={user.email} />

      <div className="flex min-w-0 flex-1 flex-col">
        <ReviewHeader
          title="Generate application"
          backLabel="Back to output"
          backTo="/generate"
          showSaveDraft={false}
        />

        <main className="min-w-0 flex-1 px-5 py-7 pl-16 sm:px-7 lg:px-10 lg:py-9 lg:pl-10">
          <div className="mx-auto max-w-[1680px]">
            <GenerationProgress activeStep={1} />

            <div className="mt-6 flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-semibold tracking-tight text-ink sm:text-[28px]">
                Add the Job Description
              </h1>
              <GenerationTypeBadge generationType={generationType} />
            </div>
            <p className="mt-1.5 text-sm text-ink-muted">{subtitle}</p>

            <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
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

              <JobContextPanel generationType={generationType} preselectedTemplateId={preselectedTemplateId} />
            </div>

            {tab === 'paste' && (
              <div className="mt-6">
                <GenerationCta
                  heading="Ready to continue?"
                  description={CTA_DESCRIPTIONS[generationType] ?? CTA_DESCRIPTIONS.RESUME_ONLY}
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
