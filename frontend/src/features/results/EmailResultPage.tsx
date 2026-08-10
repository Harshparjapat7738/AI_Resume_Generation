import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { AppHeader } from '@/components/layout/AppHeader';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { generateEmail, getApplication, getEmail } from '@/services/applicationApi';

/**
 * See ARCHITECTURE_DECISIONS.md ADR-019. The subject and the greeting/closing/signature
 * frame of the body are assembled deterministically from verified data (job title, company,
 * the candidate's own stated name) — only the highlight paragraph in the middle is
 * model-generated, and it's grounded the same way resume content is (see the "Requirement
 * coverage"-style traceability implied by `email.highlights`, not rendered here since the
 * body already reads naturally on its own).
 */
export function EmailResultPage() {
  const { applicationId = '' } = useParams<{ applicationId: string }>();
  const queryClient = useQueryClient();
  const [copiedSubject, setCopiedSubject] = useState(false);
  const [copiedEmail, setCopiedEmail] = useState(false);
  const [copyFailed, setCopyFailed] = useState(false);

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
      <div className="min-h-screen bg-void">
        <AppHeader />
        <main className="mx-auto max-w-2xl px-6 py-12">
          <ErrorBanner error={emailQuery.error ?? new Error('Email not found')} />
          <Link to="/generate" className="mt-4 inline-block text-sm text-ink-muted hover:text-ink">
            Start a new application
          </Link>
        </main>
      </div>
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

  const download = () => {
    const blob = new Blob([`Subject: ${email.subject}\n\n${email.body}`], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const link = window.document.createElement('a');
    link.href = url;
    link.download = `email-${application?.jobTitle ?? applicationId}.txt`.replace(/\s+/g, '-').toLowerCase();
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="min-h-screen bg-void">
      <AppHeader />
      <main className="mx-auto max-w-3xl px-6 py-12">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p className="text-sm font-medium uppercase tracking-wide text-ember-soft">Result</p>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight text-ink">
              {application?.jobTitle ?? 'Your application email'}
              {application?.company ? ` — ${application.company}` : ''}
            </h1>
            <p className="mt-1 text-xs text-ink-faint">
              Generated {new Date(email.createdAt).toLocaleString()}
              {email.version > 1 ? ` — version ${email.version}` : ''}
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button variant="secondary" onClick={() => copy(email.subject, setCopiedSubject)}>
              {copiedSubject ? 'Copied' : copyFailed ? "Couldn't copy" : 'Copy Subject'}
            </Button>
            <Button
              variant="secondary"
              onClick={() => copy(`Subject: ${email.subject}\n\n${email.body}`, setCopiedEmail)}
            >
              {copiedEmail ? 'Copied' : copyFailed ? "Couldn't copy" : 'Copy Email'}
            </Button>
            <Button variant="secondary" onClick={download}>
              Download
            </Button>
            <Button onClick={() => regenerate.mutate()} loading={regenerate.isPending}>
              Regenerate
            </Button>
          </div>
        </div>

        {regenerate.isError && (
          <div className="mt-4">
            <ErrorBanner error={regenerate.error} />
          </div>
        )}

        {email.removedParagraphs.length > 0 && (
          <div className="mt-6 rounded-xl border border-ember/30 bg-ember/10 px-4 py-3 text-sm text-ink">
            {email.removedParagraphs.length} paragraph{email.removedParagraphs.length === 1 ? '' : 's'} couldn't be
            verified after a retry and were replaced with a general statement rather than shown unverified.
          </div>
        )}

        <div className="mt-6 space-y-4">
          <Card>
            <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Subject</p>
            <p className="mt-2 text-sm font-medium text-ink">{email.subject}</p>
          </Card>

          <Card>
            <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Body</p>
            <div className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-ink">{email.body}</div>
          </Card>
        </div>

        <div className="mt-10 flex justify-center gap-6">
          <Link to="/generate" className="text-sm text-ink-muted hover:text-ink">
            Start another application
          </Link>
          <Link to="/dashboard" className="text-sm text-ink-muted hover:text-ink">
            View all applications
          </Link>
        </div>
      </main>
    </div>
  );
}
