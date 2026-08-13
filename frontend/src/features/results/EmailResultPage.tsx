import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { DashboardShell } from '@/features/dashboard/components/DashboardShell';
import { CopyIcon, DownloadIcon, RefreshIcon } from '@/features/dashboard/icons';
import { generateEmail, getApplication, getEmail } from '@/services/applicationApi';
import { DownloadingOverlay } from './components/DownloadingOverlay';
import { NextSteps } from './components/NextSteps';
import { ResultTopBar } from './components/ResultTopBar';

/**
 * Dashboard-shell layout for the email result page — same structure as `ResultPage`/
 * `CoverLetterResultPage` (compact `ResultTopBar`, a hero card with the actions,
 * `DownloadingOverlay` on download, `NextSteps` footer) for UI parity across every generated
 * output. Every query/mutation/handler below is otherwise unchanged: `getApplication`,
 * `getEmail`, `generateEmail` (regenerate), both copy actions and the plain-text download all
 * do exactly what they did before — only the presentation and the download's animation/
 * redirect are new. See ARCHITECTURE_DECISIONS.md ADR-019 for how the email body is grounded.
 */
export function EmailResultPage() {
  const { applicationId = '' } = useParams<{ applicationId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [copiedSubject, setCopiedSubject] = useState(false);
  const [copiedEmail, setCopiedEmail] = useState(false);
  const [copyFailed, setCopyFailed] = useState(false);
  const [downloadStage, setDownloadStage] = useState<'idle' | 'downloading' | 'done'>('idle');

  const applicationQuery = useQuery({
    queryKey: ['application', applicationId],
    queryFn: () => getApplication(applicationId),
    enabled: Boolean(applicationId),
  });

  const emailQuery = useQuery({
    queryKey: ['email', applicationId],
    queryFn: () => getEmail(applicationId),
    enabled: Boolean(applicationId),
  });

  const regenerate = useMutation({
    mutationFn: () => generateEmail(applicationId),
    onSuccess: (email) => queryClient.setQueryData(['email', applicationId], email),
  });

  if (emailQuery.isLoading) {
    return <FullScreenSpinner label="Loading your email…" />;
  }

  if (emailQuery.isError || !emailQuery.data) {
    return (
      <DashboardShell>
        {({ user, onLogout }) => (
          <>
            <ResultTopBar user={user} onLogout={onLogout} />
            <div className="mt-6">
              <ErrorBanner error={emailQuery.error ?? new Error('Email not found')} />
              <Link to="/generate" className="mt-4 inline-block text-sm text-ink-muted hover:text-ink">
                Start a new application
              </Link>
            </div>
          </>
        )}
      </DashboardShell>
    );
  }

  const email = emailQuery.data;
  const application = applicationQuery.data;

  const copy = async (text: string, mark: (value: boolean) => void) => {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      setCopyFailed(true);
      setTimeout(() => setCopyFailed(false), 2000);
      return;
    }
    mark(true);
    setTimeout(() => mark(false), 2000);
  };

  // Same reasoning as CoverLetterResultPage: a plain-text file assembled entirely client-side,
  // nothing that can fail server-side. The short "downloading" pause is on a real, already-
  // saved file, kept only so the animation matches the resume PDF's pacing (redesign point 4:
  // "same UI from start to end"), not a fabricated delay.
  const download = () => {
    setDownloadStage('downloading');
    const blob = new Blob([`Subject: ${email.subject}\n\n${email.body}`], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const link = window.document.createElement('a');
    link.href = url;
    link.download = `email-${application?.jobTitle ?? applicationId}.txt`.replace(/\s+/g, '-').toLowerCase();
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
                    {application?.jobTitle ?? 'Your application email'}
                    {application?.company ? <span className="text-ink-faint"> — {application.company}</span> : null}
                  </h1>
                  <p className="mt-2 text-sm text-ink-faint">
                    Generated {new Date(email.createdAt).toLocaleString()}
                    {email.version > 1 ? ` · version ${email.version}` : ''}
                  </p>
                </div>
                <div className="flex w-full flex-wrap gap-3 sm:w-auto sm:shrink-0">
                  <button
                    type="button"
                    onClick={() => copy(email.subject, setCopiedSubject)}
                    className="flex items-center gap-2 rounded-full border border-border-strong px-4 py-2.5 text-sm font-medium text-ink-muted transition-colors hover:border-ink-muted hover:text-ink"
                  >
                    <CopyIcon className="h-4 w-4" />
                    {copiedSubject ? 'Copied' : copyFailed ? "Couldn't copy" : 'Copy Subject'}
                  </button>
                  <button
                    type="button"
                    onClick={() => copy(`Subject: ${email.subject}\n\n${email.body}`, setCopiedEmail)}
                    className="flex items-center gap-2 rounded-full border border-border-strong px-4 py-2.5 text-sm font-medium text-ink-muted transition-colors hover:border-ink-muted hover:text-ink"
                  >
                    <CopyIcon className="h-4 w-4" />
                    {copiedEmail ? 'Copied' : copyFailed ? "Couldn't copy" : 'Copy Email'}
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

            {email.removedParagraphs.length > 0 && (
              <div className="rounded-xl border border-ember/30 bg-ember/10 px-4 py-3 text-sm text-ink">
                {email.removedParagraphs.length} paragraph{email.removedParagraphs.length === 1 ? '' : 's'} couldn't be
                verified after a retry and {email.removedParagraphs.length === 1 ? 'was' : 'were'} replaced with a
                general statement rather than shown unverified.
              </div>
            )}

            <Card>
              <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Subject</p>
              <p className="mt-2 text-sm font-medium text-ink">{email.subject}</p>
            </Card>

            <Card>
              <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Body</p>
              <div className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-ink">{email.body}</div>
            </Card>

            <NextSteps />
          </div>
        </>
      )}
    </DashboardShell>
  );
}
