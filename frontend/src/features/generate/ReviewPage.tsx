import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { confirmJd, getAnalysis, getJd } from '@/services/jdApi';
import { COVER_LETTER_STEPS, EMAIL_STEPS, GenerateLayout } from './GenerateLayout';

export function ReviewPage() {
  const { jdId = '' } = useParams<{ jdId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const generationType = searchParams.get('type') ?? 'RESUME_ONLY';
  const isEmailOnly = generationType === 'EMAIL_ONLY';
  const isCoverLetterOnly = generationType === 'COVER_LETTER_ONLY';

  const jdQuery = useQuery({ queryKey: ['jd', jdId], queryFn: () => getJd(jdId), enabled: Boolean(jdId) });

  const confirmMutation = useMutation({
    mutationFn: () => confirmJd(jdId),
    onSuccess: (summary) => {
      queryClient.setQueryData(['jd', jdId], (prev: typeof jdQuery.data) =>
        prev ? { ...prev, status: summary.status } : prev,
      );
    },
  });

  const isConfirmed = jdQuery.data?.status === 'CONFIRMED';

  const analysisQuery = useQuery({
    queryKey: ['jd-analysis', jdId],
    queryFn: () => getAnalysis(jdId),
    enabled: isConfirmed,
    retry: false,
  });

  if (jdQuery.isLoading) {
    return <FullScreenSpinner label="Loading the job description…" />;
  }

  if (jdQuery.isError || !jdQuery.data) {
    return (
      <GenerateLayout activeStep={2} title="Review" subtitle="">
        <ErrorBanner error={jdQuery.error} />
      </GenerateLayout>
    );
  }

  const jd = jdQuery.data;
  const hasUrlPreview = jd.sourceType === 'URL'
    && (jd.title || jd.company || jd.location || jd.skillsSummary || jd.experienceSummary);

  return (
    <GenerateLayout
      activeStep={2}
      title="Confirm and review"
      subtitle="Nothing is generated until you confirm this is the job you're applying to."
      steps={isEmailOnly ? EMAIL_STEPS : isCoverLetterOnly ? COVER_LETTER_STEPS : undefined}
    >
      {jd.sourceType === 'URL' && (
        <Card className="mb-4">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
            Fetched from URL
          </p>
          <p className="mt-1 truncate text-xs text-ink-faint">{jd.sourceUrl}</p>
          {hasUrlPreview ? (
            <dl className="mt-4 grid gap-3 sm:grid-cols-2">
              {jd.title && (
                <div>
                  <dt className="text-xs text-ink-faint">Job Title</dt>
                  <dd className="text-sm text-ink">{jd.title}</dd>
                </div>
              )}
              {jd.company && (
                <div>
                  <dt className="text-xs text-ink-faint">Company</dt>
                  <dd className="text-sm text-ink">{jd.company}</dd>
                </div>
              )}
              {jd.location && (
                <div>
                  <dt className="text-xs text-ink-faint">Location</dt>
                  <dd className="text-sm text-ink">{jd.location}</dd>
                </div>
              )}
              {jd.experienceSummary && (
                <div>
                  <dt className="text-xs text-ink-faint">Experience</dt>
                  <dd className="text-sm text-ink">{jd.experienceSummary}</dd>
                </div>
              )}
              {jd.skillsSummary && (
                <div className="sm:col-span-2">
                  <dt className="text-xs text-ink-faint">Required Skills</dt>
                  <dd className="text-sm text-ink">{jd.skillsSummary}</dd>
                </div>
              )}
            </dl>
          ) : (
            <p className="mt-3 text-sm text-ink-muted">
              This page didn't publish structured job data, so only the extracted text below
              is available — review it the same way you'd review pasted text.
            </p>
          )}
        </Card>
      )}

      <Card>
        <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
          {jd.sourceType === 'URL' ? 'Extracted description' : 'Submitted text'}
        </p>
        <div className="mt-3 max-h-64 overflow-y-auto whitespace-pre-wrap rounded-xl border border-border bg-void p-4 text-sm leading-relaxed text-ink-muted">
          {jd.rawText}
        </div>
      </Card>

      {!isConfirmed ? (
        <div className="mt-6">
          {confirmMutation.isError && <ErrorBanner error={confirmMutation.error} />}
          <Button onClick={() => confirmMutation.mutate()} loading={confirmMutation.isPending}>
            Confirm this is correct
          </Button>
        </div>
      ) : (
        <div className="mt-6">
          {analysisQuery.isLoading && (
            <div className="flex items-center gap-3 text-sm text-ink-muted">
              <span
                className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
                aria-hidden="true"
              />
              Analysing requirements against the job description…
            </div>
          )}
          {analysisQuery.isError && <ErrorBanner error={analysisQuery.error} />}
          {analysisQuery.data && (
            <div className="space-y-4">
              <Card>
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <h2 className="text-base font-semibold text-ink">
                    {analysisQuery.data.title ?? 'Untitled role'}
                    {analysisQuery.data.company ? ` at ${analysisQuery.data.company}` : ''}
                  </h2>
                  {analysisQuery.data.seniority && (
                    <span className="rounded-full border border-border-strong px-2.5 py-1 text-xs text-ink-muted">
                      {analysisQuery.data.seniority}
                    </span>
                  )}
                </div>
                <ul className="mt-4 space-y-2">
                  {analysisQuery.data.requirements.map((req) => (
                    <li
                      key={req.requirementId}
                      className="flex items-start justify-between gap-3 rounded-lg border border-border bg-void px-3 py-2 text-sm"
                    >
                      <span className="text-ink-muted">{req.text}</span>
                      <span className="shrink-0 rounded-full bg-surface-2 px-2 py-0.5 text-xs text-ink-faint">
                        {req.type}
                      </span>
                    </li>
                  ))}
                </ul>
              </Card>

              <div className="flex justify-end">
                {isEmailOnly ? (
                  <Button onClick={() => navigate(`/generate/processing/${jdId}?type=EMAIL_ONLY`)}>
                    Generate my email
                  </Button>
                ) : isCoverLetterOnly ? (
                  <Button onClick={() => navigate(`/generate/processing/${jdId}?type=COVER_LETTER_ONLY`)}>
                    Generate my cover letter
                  </Button>
                ) : (
                  <Button onClick={() => navigate(`/generate/template/${jdId}`)}>Choose a template</Button>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </GenerateLayout>
  );
}
