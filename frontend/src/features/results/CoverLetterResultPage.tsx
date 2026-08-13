import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { DashboardShell } from '@/features/dashboard/components/DashboardShell';
import { CopyIcon, DownloadIcon, RefreshIcon } from '@/features/dashboard/icons';
import { generateCoverLetter, getApplication, getCoverLetter } from '@/services/applicationApi';
import type { CoverLetterVersion } from '@/services/applicationApi';
import { DownloadingOverlay } from './components/DownloadingOverlay';
import { NextSteps } from './components/NextSteps';
import { ResultTopBar } from './components/ResultTopBar';

/**
 * See ARCHITECTURE_DECISIONS.md ADR-020. Every paragraph is model-generated and grounded the
 * same way resume content is (each cites the evidence it draws on) — greeting and sign-off
 * are boilerplate, not factual claims, so they're rendered as-is without a citation.
 *
 * <p>A paragraph the grounding degrade path removed (both attempts failed verification) is
 * simply absent from `content` rather than shown unverified — {@link letterText} renders
 * whatever survived, and {@code removedParagraphs} discloses the gap honestly instead of
 * papering over it with invented text.
 */
export function letterText(content: CoverLetterVersion['content']): string {
  const parts: string[] = [];
  if (content.greeting) parts.push(content.greeting);
  if (content.openingParagraph?.text) parts.push(content.openingParagraph.text);
  for (const paragraph of content.bodyParagraphs ?? []) {
    if (paragraph.text) parts.push(paragraph.text);
  }
  if (content.closingParagraph?.text) parts.push(content.closingParagraph.text);
  if (content.signOff) parts.push(content.signOff);
  return parts.join('\n\n');
}

/**
 * Dashboard-shell layout for the cover-letter result page — same structure as `ResultPage`
 * (compact `ResultTopBar`, a hero card with the actions, `DownloadingOverlay` on download,
 * `NextSteps` footer) so every generated-output result reads as one consistent experience
 * rather than the resume page alone having the newer look. Every query/mutation/handler below
 * is otherwise unchanged from the previous version of this page: `getApplication`,
 * `getCoverLetter`, `generateCoverLetter` (regenerate), copy-to-clipboard and the plain-text
 * download all do exactly what they did before — only the presentation and the download's
 * animation/redirect are new.
 */
export function CoverLetterResultPage() {
  const { applicationId = '' } = useParams<{ applicationId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [copied, setCopied] = useState(false);
  const [copyFailed, setCopyFailed] = useState(false);
  const [downloadStage, setDownloadStage] = useState<'idle' | 'downloading' | 'done'>('idle');

  const applicationQuery = useQuery({
    queryKey: ['application', applicationId],
    queryFn: () => getApplication(applicationId),
    enabled: Boolean(applicationId),
  });

  const coverLetterQuery = useQuery({
    queryKey: ['cover-letter', applicationId],
    queryFn: () => getCoverLetter(applicationId),
    enabled: Boolean(applicationId),
  });

  const regenerate = useMutation({
    mutationFn: () => generateCoverLetter(applicationId),
    onSuccess: (version) => queryClient.setQueryData(['cover-letter', applicationId], version),
  });

  if (coverLetterQuery.isLoading) {
    return <FullScreenSpinner label="Loading your cover letter…" />;
  }

  if (coverLetterQuery.isError || !coverLetterQuery.data) {
    return (
      <DashboardShell>
        {({ user, onLogout }) => (
          <>
            <ResultTopBar user={user} onLogout={onLogout} />
            <div className="mt-6">
              <ErrorBanner error={coverLetterQuery.error ?? new Error('Cover letter not found')} />
              <Link to="/generate" className="mt-4 inline-block text-sm text-ink-muted hover:text-ink">
                Start a new application
              </Link>
            </div>
          </>
        )}
      </DashboardShell>
    );
  }

  const coverLetter = coverLetterQuery.data;
  const application = applicationQuery.data;
  const text = letterText(coverLetter.content);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      setCopyFailed(true);
      setTimeout(() => setCopyFailed(false), 2000);
      return;
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // A plain-text file, built and saved entirely client-side — there's no document-service
  // render step the way the resume PDF has, so there's nothing that can fail server-side here.
  // The brief "downloading" stage is a deliberate, honest pause on a real, already-completed
  // save (not a fabricated wait) purely so the animation reads the same as the resume PDF's,
  // matching "same UI from start to end" rather than one page flashing past it instantly.
  const download = () => {
    setDownloadStage('downloading');
    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const link = window.document.createElement('a');
    link.href = url;
    link.download = `cover-letter-${application?.jobTitle ?? applicationId}.txt`.replace(/\s+/g, '-').toLowerCase();
    link.click();
    URL.revokeObjectURL(url);
    setTimeout(() => setDownloadStage('done'), 500);
  };

  return (
    <DashboardShell>
      {({ user, onLogout }) => (
        <>
          {downloadStage !== 'idle' && (
            <DownloadingOverlay
              stage={downloadStage === 'downloading' ? 'downloading' : 'done'}
              onGoToDashboard={() => navigate('/dashboard')}
            />
          )}

          <ResultTopBar user={user} onLogout={onLogout} />

          <div className="mt-6 space-y-6">
            <Card className="!p-6 sm:!p-8">
              <div className="flex flex-wrap items-start justify-between gap-6">
                <div className="min-w-0">
                  <p className="text-xs font-semibold uppercase tracking-wide text-ember-soft">Result</p>
                  <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
                    {application?.jobTitle ?? coverLetter.jobTitle ?? 'Your cover letter'}
                    {(application?.company ?? coverLetter.company) ? (
                      <span className="text-ink-faint"> — {application?.company ?? coverLetter.company}</span>
                    ) : null}
                  </h1>
                  <p className="mt-2 text-sm text-ink-faint">
                    Generated {new Date(coverLetter.createdAt).toLocaleString()}
                    {coverLetter.version > 1 ? ` · version ${coverLetter.version}` : ''}
                  </p>
                </div>
                <div className="flex w-full flex-wrap gap-3 sm:w-auto sm:shrink-0">
                  <button
                    type="button"
                    onClick={copy}
                    className="flex items-center gap-2 rounded-full border border-border-strong px-4 py-2.5 text-sm font-medium text-ink-muted transition-colors hover:border-ink-muted hover:text-ink"
                  >
                    <CopyIcon className="h-4 w-4" />
                    {copied ? 'Copied' : copyFailed ? "Couldn't copy" : 'Copy'}
                  </button>
                  <button
                    type="button"
                    onClick={download}
                    className="flex items-center gap-2 rounded-full bg-linear-to-r from-ember to-rose px-5 py-2.5 text-sm font-semibold text-void transition-opacity hover:opacity-90"
                  >
                    <DownloadIcon className="h-4 w-4" />
                    Download
                  </button>
                  <button
                    type="button"
                    onClick={() => regenerate.mutate()}
                    disabled={regenerate.isPending}
                    className="flex items-center gap-2 rounded-full border border-border-strong px-4 py-2.5 text-sm font-medium text-ink-muted transition-colors hover:border-ink-muted hover:text-ink disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    <RefreshIcon className={`h-4 w-4 ${regenerate.isPending ? 'animate-spin' : ''}`} />
                    Regenerate
                  </button>
                </div>
              </div>
            </Card>

            {regenerate.isError && <ErrorBanner error={regenerate.error} />}

            {coverLetter.removedParagraphs.length > 0 && (
              <div className="rounded-xl border border-ember/30 bg-ember/10 px-4 py-3 text-sm text-ink">
                {coverLetter.removedParagraphs.length} paragraph{coverLetter.removedParagraphs.length === 1 ? '' : 's'}{' '}
                couldn't be verified after a retry and {coverLetter.removedParagraphs.length === 1 ? 'was' : 'were'}{' '}
                removed rather than shown unverified.
              </div>
            )}

            <Card>
              <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Cover Letter</p>
              <div className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-ink">{text}</div>
            </Card>

            <NextSteps />
          </div>
        </>
      )}
    </DashboardShell>
  );
}
