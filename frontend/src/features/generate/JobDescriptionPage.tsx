import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { TextArea } from '@/components/ui/TextArea';
import { TextField } from '@/components/ui/TextField';
import { ApiError } from '@/services/apiClient';
import { fetchJdFromUrl, submitJd } from '@/services/jdApi';
import { COVER_LETTER_STEPS, EMAIL_STEPS, GenerateLayout } from './GenerateLayout';

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

export function JobDescriptionPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  // Carried as a query param (not route state) so it survives a reload, matching how
  // TemplatePage later carries templateId to ProcessingPage. Defaults to the resume flow so a
  // direct/bookmarked link to this page keeps working exactly as before.
  const generationType = searchParams.get('type') ?? 'RESUME_ONLY';
  // Optional — set only when arriving from the Templates page's "Use this template" action, so
  // TemplatePage can preselect it instead of the user having to pick it again. Just forwarded
  // along here, same as `type`; this page has no template UI of its own.
  const preselectedTemplateId = searchParams.get('templateId');
  const templateParam = preselectedTemplateId ? `&templateId=${preselectedTemplateId}` : '';
  const [tab, setTab] = useState<'paste' | 'url'>('paste');
  const [submitError, setSubmitError] = useState<unknown>(null);
  const [urlError, setUrlError] = useState<unknown>(null);

  const pasteForm = useForm<PasteFormValues>({ resolver: zodResolver(pasteSchema) });
  const urlForm = useForm<UrlFormValues>({ resolver: zodResolver(urlSchema) });

  const length = pasteForm.watch('jobDescriptionText')?.length ?? 0;

  const onSubmitPaste = async (values: PasteFormValues) => {
    setSubmitError(null);
    try {
      const jd = await submitJd(values.jobDescriptionText);
      navigate(`/generate/review/${jd.id}?type=${generationType}${templateParam}`);
    } catch (error) {
      setSubmitError(error);
    }
  };

  const onSubmitUrl = async (values: UrlFormValues) => {
    setUrlError(null);
    try {
      const jd = await fetchJdFromUrl(values.url);
      navigate(`/generate/review/${jd.id}?type=${generationType}${templateParam}`);
    } catch (error) {
      setUrlError(error);
    }
  };

  const switchToPaste = () => {
    setUrlError(null);
    setTab('paste');
  };

  // "Unable to extract" covers both a fetch that failed outright (JD_VALIDATION_ERROR) and
  // one the SSRF guard rejected (JD_URL_BLOCKED) — the user doesn't need to know which; the
  // fallback is the same either way.
  const urlErrorMessage =
    urlError instanceof ApiError && (urlError.body.code === 'JD_VALIDATION_ERROR' || urlError.body.code === 'JD_URL_BLOCKED')
      ? 'Unable to extract this job description from this URL.'
      : null;

  return (
    <GenerateLayout
      activeStep={1}
      title="Add the job description"
      subtitle="You'll confirm the exact text before anything is generated."
      steps={
        generationType === 'EMAIL_ONLY'
          ? EMAIL_STEPS
          : generationType === 'COVER_LETTER_ONLY'
            ? COVER_LETTER_STEPS
            : undefined
      }
    >
      <div className="mb-5 inline-flex rounded-full border border-border bg-surface p-1 text-sm">
        <button
          type="button"
          onClick={() => setTab('paste')}
          className={`rounded-full px-4 py-1.5 transition-colors ${
            tab === 'paste' ? 'bg-ink text-void' : 'text-ink-muted hover:text-ink'
          }`}
        >
          Paste Job Description
        </button>
        <button
          type="button"
          onClick={() => setTab('url')}
          className={`rounded-full px-4 py-1.5 transition-colors ${
            tab === 'url' ? 'bg-ink text-void' : 'text-ink-muted hover:text-ink'
          }`}
        >
          Job URL
        </button>
      </div>

      {tab === 'url' ? (
        <div className="space-y-4">
          <form className="space-y-4" onSubmit={urlForm.handleSubmit(onSubmitUrl)} noValidate>
            <TextField
              label="Job posting URL"
              type="url"
              placeholder="https://company.com/careers/software-engineer"
              error={urlForm.formState.errors.url?.message}
              {...urlForm.register('url')}
            />
            <p className="text-xs text-ink-faint">
              Works best on company career pages and ATS platforms (Greenhouse, Lever, Workday
              and similar) that publish structured job data. LinkedIn and Indeed often block
              automated fetches — we don't attempt to bypass that.
            </p>
            <div className="flex justify-end">
              <Button type="submit" loading={urlForm.formState.isSubmitting}>
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
        <form className="space-y-4" onSubmit={pasteForm.handleSubmit(onSubmitPaste)} noValidate>
          {submitError !== null ? <ErrorBanner error={submitError} /> : null}
          <TextArea
            label="Job description"
            rows={14}
            placeholder="Paste the full job posting here…"
            error={pasteForm.formState.errors.jobDescriptionText?.message}
            {...pasteForm.register('jobDescriptionText')}
          />
          <p className="text-xs text-ink-faint">{length.toLocaleString()} / 60,000 characters</p>
          <div className="flex justify-end">
            <Button type="submit" loading={pasteForm.formState.isSubmitting}>
              Continue
            </Button>
          </div>
        </form>
      )}
    </GenerateLayout>
  );
}
