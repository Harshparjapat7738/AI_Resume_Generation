import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { AppHeader } from '@/components/layout/AppHeader';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { generateCoverLetter, getApplication, getCoverLetter } from '@/services/applicationApi';
import type { CoverLetterVersion } from '@/services/applicationApi';

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
function letterText(content: CoverLetterVersion['content']): string {
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

export function CoverLetterResultPage() {
  const { applicationId = '' } = useParams<{ applicationId: string }>();
  const queryClient = useQueryClient();
  const [copied, setCopied] = useState(false);
  const [copyFailed, setCopyFailed] = useState(false);

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
      <div className="min-h-screen bg-void">
        <AppHeader />
        <main className="mx-auto max-w-2xl px-6 py-12">
          <ErrorBanner error={coverLetterQuery.error ?? new Error('Cover letter not found')} />
          <Link to="/generate" className="mt-4 inline-block text-sm text-ink-muted hover:text-ink">
            Start a new application
          </Link>
        </main>
      </div>
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

  const download = () => {
    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const link = window.document.createElement('a');
    link.href = url;
    link.download = `cover-letter-${application?.jobTitle ?? applicationId}.txt`.replace(/\s+/g, '-').toLowerCase();
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
              {application?.jobTitle ?? coverLetter.jobTitle ?? 'Your cover letter'}
              {(application?.company ?? coverLetter.company) ? ` — ${application?.company ?? coverLetter.company}` : ''}
            </h1>
            <p className="mt-1 text-xs text-ink-faint">
              Generated {new Date(coverLetter.createdAt).toLocaleString()}
              {coverLetter.version > 1 ? ` — version ${coverLetter.version}` : ''}
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button variant="secondary" onClick={copy}>
              {copied ? 'Copied' : copyFailed ? "Couldn't copy" : 'Copy'}
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

        {coverLetter.removedParagraphs.length > 0 && (
          <div className="mt-6 rounded-xl border border-ember/30 bg-ember/10 px-4 py-3 text-sm text-ink">
            {coverLetter.removedParagraphs.length} paragraph{coverLetter.removedParagraphs.length === 1 ? '' : 's'}{' '}
            couldn't be verified after a retry and {coverLetter.removedParagraphs.length === 1 ? 'was' : 'were'}{' '}
            removed rather than shown unverified.
          </div>
        )}

        <div className="mt-6 space-y-4">
          <Card>
            <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Cover Letter</p>
            <div className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-ink">{text}</div>
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
