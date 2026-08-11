import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { AppHeader } from '@/components/layout/AppHeader';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import {
  attachResume,
  generateCoverLetter,
  generateEmail,
  getApplication,
  getCoverLetter,
  getEmail,
  recordOutputFailure,
} from '@/services/applicationApi';
import { assessResume, getAssessment } from '@/services/assessmentApi';
import { downloadDocument, getRenderedDocument, renderResumePdf, type RenderedDocument } from '@/services/documentApi';
import { generateResume, getResume } from '@/services/resumeApi';
import { AtsChecklist } from './components/AtsChecklist';
import { EvidenceMatches } from './components/EvidenceMatches';
import { GapsList } from './components/GapsList';
import { GroundingBanner } from './components/GroundingBanner';
import { JdFitBreakdown } from './components/JdFitBreakdown';
import { KeywordsPanel } from './components/KeywordsPanel';
import { RecommendationsList } from './components/RecommendationsList';
import { contentAsPlainText, ResumeContentView } from './components/ResumeContentView';
import { ScoreCard } from './components/ScoreCard';
import { letterText } from './CoverLetterResultPage';

type Tab = 'resume' | 'coverLetter' | 'email' | 'ats' | 'jdFit';

const TABS: { id: Tab; label: string }[] = [
  { id: 'resume', label: 'Resume' },
  { id: 'coverLetter', label: 'Cover Letter' },
  { id: 'email', label: 'Email' },
  { id: 'ats', label: 'ATS Analysis' },
  { id: 'jdFit', label: 'JD Fit' },
];

function messageOf(err: unknown): string {
  return err instanceof Error && err.message ? err.message : 'Generation failed.';
}

function outputBadge(label: string, done: boolean, failed: boolean) {
  const style = done
    ? 'bg-mint/10 text-mint'
    : failed
      ? 'bg-ember/10 text-ember-soft'
      : 'bg-surface-2 text-ink-faint';
  const mark = done ? '✓' : failed ? '✕' : '…';
  return (
    <span key={label} className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${style}`}>
      {label} {mark}
    </span>
  );
}

function FailedOutput({ reason, onRetry, pending }: { reason: string | null; onRetry: () => void; pending: boolean }) {
  return (
    <Card>
      <p className="text-sm font-medium text-ink">This output failed to generate.</p>
      {reason && <p className="mt-1 text-xs text-ink-faint">{reason}</p>}
      <Button className="mt-4" onClick={onRetry} loading={pending}>
        Retry
      </Button>
    </Card>
  );
}

function NotGenerated({ label }: { label: string }) {
  return (
    <Card>
      <p className="text-sm text-ink-muted">{label} hasn't been generated for this application yet.</p>
    </Card>
  );
}

/**
 * "Generate All" result page — one `Application` (`GenerationType.ALL`), three independently
 * tracked outputs. Reuses the exact display logic and download/copy actions the single-output
 * result pages already have (`ResultPage`, `CoverLetterResultPage`, `EmailResultPage`) rather
 * than reimplementing them — only the tab shell, the status tracker and per-output retry are
 * new. Every piece of state driving this page (`resumeVersionId`/`coverLetterVersionId`/
 * `emailId`/`resumeError`/`coverLetterError`/`emailError`) lives on the `Application` itself,
 * so a refresh re-fetches the same picture rather than losing it.
 */
export function AllResultPage() {
  const { applicationId = '' } = useParams<{ applicationId: string }>();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>('resume');
  const [copied, setCopied] = useState<string | null>(null);
  const [pdfBlobUrl, setPdfBlobUrl] = useState<string | null>(null);
  const pdfBlobUrlRef = useRef<string | null>(null);
  useEffect(() => {
    pdfBlobUrlRef.current = pdfBlobUrl;
  }, [pdfBlobUrl]);
  useEffect(() => () => {
    if (pdfBlobUrlRef.current) URL.revokeObjectURL(pdfBlobUrlRef.current);
  }, []);

  const applicationQuery = useQuery({
    queryKey: ['application', applicationId],
    queryFn: () => getApplication(applicationId),
    enabled: Boolean(applicationId),
  });
  const application = applicationQuery.data;

  const resumeId = application?.resumeVersionId ?? null;
  const resumeQuery = useQuery({
    queryKey: ['resume', resumeId],
    queryFn: () => getResume(resumeId!),
    enabled: Boolean(resumeId),
  });
  const assessmentQuery = useQuery({
    queryKey: ['assessment', resumeId],
    queryFn: () => getAssessment(resumeId!),
    enabled: Boolean(resumeId),
    retry: false,
  });
  const coverLetterQuery = useQuery({
    queryKey: ['cover-letter', applicationId],
    queryFn: () => getCoverLetter(applicationId),
    enabled: Boolean(application?.coverLetterVersionId),
  });
  const emailQuery = useQuery({
    queryKey: ['email', applicationId],
    queryFn: () => getEmail(applicationId),
    enabled: Boolean(application?.emailId),
  });
  const renderedDocumentQuery = useQuery({
    queryKey: ['rendered-document', resumeId],
    queryFn: () => getRenderedDocument(resumeId!),
    enabled: Boolean(resumeId),
    retry: false,
  });

  const refreshApplication = () => queryClient.invalidateQueries({ queryKey: ['application', applicationId] });

  const retryResume = useMutation({
    mutationFn: async () => {
      if (!application) throw new Error('Application not loaded yet.');
      try {
        const resume = await generateResume(application.jobDescriptionId, application.templateId ?? undefined);
        await attachResume(application.id, resume.id);
        try {
          await assessResume(resume.id);
        } catch {
          // Non-fatal, same as every other resume flow.
        }
      } catch (err) {
        await recordOutputFailure(application.id, 'resume', messageOf(err));
        throw err;
      }
    },
    onSettled: refreshApplication,
  });

  const retryCoverLetter = useMutation({
    mutationFn: async () => {
      if (!application) throw new Error('Application not loaded yet.');
      try {
        await generateCoverLetter(application.id);
      } catch (err) {
        await recordOutputFailure(application.id, 'coverLetter', messageOf(err));
        throw err;
      }
    },
    onSettled: () => {
      refreshApplication();
      queryClient.invalidateQueries({ queryKey: ['cover-letter', applicationId] });
    },
  });

  const retryEmail = useMutation({
    mutationFn: async () => {
      if (!application) throw new Error('Application not loaded yet.');
      try {
        await generateEmail(application.id);
      } catch (err) {
        await recordOutputFailure(application.id, 'email', messageOf(err));
        throw err;
      }
    },
    onSettled: () => {
      refreshApplication();
      queryClient.invalidateQueries({ queryKey: ['email', applicationId] });
    },
  });

  const preparePdf = useMutation({
    mutationFn: async (): Promise<{ document: RenderedDocument; blob: Blob }> => {
      const existing = renderedDocumentQuery.data ?? (await renderResumePdf(resumeId!));
      const blob = await downloadDocument(existing.id);
      return { document: existing, blob };
    },
    onSuccess: ({ document: rendered, blob }) => {
      queryClient.setQueryData(['rendered-document', resumeId], rendered);
      const url = URL.createObjectURL(blob);
      setPdfBlobUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return url;
      });
      const link = window.document.createElement('a');
      link.href = url;
      link.download = `resume-${application?.jobTitle ?? applicationId}.pdf`.replace(/\s+/g, '-').toLowerCase();
      link.click();
    },
  });

  const copy = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(label);
      setTimeout(() => setCopied(null), 2000);
    } catch {
      setCopied(`${label}-failed`);
      setTimeout(() => setCopied(null), 2000);
    }
  };

  const downloadText = (filename: string, text: string) => {
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = window.document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  };

  if (applicationQuery.isLoading) {
    return <FullScreenSpinner label="Loading your application…" />;
  }

  if (applicationQuery.isError || !application) {
    return (
      <div className="min-h-screen bg-void">
        <AppHeader />
        <main className="mx-auto max-w-2xl px-6 py-12">
          <ErrorBanner error={applicationQuery.error ?? new Error('Application not found')} />
          <Link to="/generate" className="mt-4 inline-block text-sm text-ink-muted hover:text-ink">
            Start a new application
          </Link>
        </main>
      </div>
    );
  }

  const resumeDone = Boolean(application.resumeVersionId);
  const resumeFailed = Boolean(application.resumeError);
  const coverLetterDone = Boolean(application.coverLetterVersionId);
  const coverLetterFailed = Boolean(application.coverLetterError);
  const emailDone = Boolean(application.emailId);
  const emailFailed = Boolean(application.emailError);
  const anyFailed = resumeFailed || coverLetterFailed || emailFailed;

  const renderResumeTab = () => {
    if (resumeFailed) {
      return <FailedOutput reason={application.resumeError} onRetry={() => retryResume.mutate()} pending={retryResume.isPending} />;
    }
    if (!resumeDone) {
      return <NotGenerated label="Resume" />;
    }
    return (
      <div className="space-y-4">
        <div className="flex flex-wrap gap-3">
          <Button
            variant="secondary"
            onClick={() => resumeQuery.data && copy(contentAsPlainText(resumeQuery.data.content), 'resume')}
          >
            {copied === 'resume' ? 'Copied' : copied === 'resume-failed' ? "Couldn't copy" : 'Copy text'}
          </Button>
          <Button onClick={() => preparePdf.mutate()} loading={preparePdf.isPending}>
            Download Resume PDF
          </Button>
        </div>
        {preparePdf.isError && <p className="text-xs text-ember-soft">PDF unavailable for this generation.</p>}
        {pdfBlobUrl && !preparePdf.isError && (
          <Card className="!p-0 overflow-hidden">
            <iframe title="Resume PDF preview" src={pdfBlobUrl} className="h-[600px] w-full rounded-2xl border-0" />
          </Card>
        )}
        {resumeQuery.data && (
          <Card>
            <ResumeContentView content={resumeQuery.data.content} />
          </Card>
        )}
      </div>
    );
  };

  const renderCoverLetterTab = () => {
    if (coverLetterFailed) {
      return (
        <FailedOutput
          reason={application.coverLetterError}
          onRetry={() => retryCoverLetter.mutate()}
          pending={retryCoverLetter.isPending}
        />
      );
    }
    if (!coverLetterDone) {
      return <NotGenerated label="Cover letter" />;
    }
    if (coverLetterQuery.isLoading) {
      return <p className="text-sm text-ink-faint">Loading…</p>;
    }
    if (coverLetterQuery.isError || !coverLetterQuery.data) {
      return <ErrorBanner error={coverLetterQuery.error ?? new Error('Cover letter not found')} />;
    }
    const content = coverLetterQuery.data;
    const text = letterText(content.content);
    const filename = `cover-letter-${application.jobTitle ?? applicationId}.txt`.replace(/\s+/g, '-').toLowerCase();
    return (
      <div className="space-y-4">
        <div className="flex flex-wrap gap-3">
          <Button variant="secondary" onClick={() => copy(text, 'cover-letter')}>
            {copied === 'cover-letter' ? 'Copied' : copied === 'cover-letter-failed' ? "Couldn't copy" : 'Copy'}
          </Button>
          <Button variant="secondary" onClick={() => downloadText(filename, text)}>
            Download
          </Button>
        </div>
        {content.removedParagraphs.length > 0 && (
          <div className="rounded-xl border border-ember/30 bg-ember/10 px-4 py-3 text-sm text-ink">
            {content.removedParagraphs.length} paragraph{content.removedParagraphs.length === 1 ? '' : 's'} couldn't
            be verified after a retry and {content.removedParagraphs.length === 1 ? 'was' : 'were'} removed rather
            than shown unverified.
          </div>
        )}
        <Card>
          <div className="whitespace-pre-wrap text-sm leading-relaxed text-ink">{text}</div>
        </Card>
      </div>
    );
  };

  const renderEmailTab = () => {
    if (emailFailed) {
      return <FailedOutput reason={application.emailError} onRetry={() => retryEmail.mutate()} pending={retryEmail.isPending} />;
    }
    if (!emailDone) {
      return <NotGenerated label="Email" />;
    }
    if (emailQuery.isLoading) {
      return <p className="text-sm text-ink-faint">Loading…</p>;
    }
    if (emailQuery.isError || !emailQuery.data) {
      return <ErrorBanner error={emailQuery.error ?? new Error('Email not found')} />;
    }
    const email = emailQuery.data;
    const filename = `email-${application.jobTitle ?? applicationId}.txt`.replace(/\s+/g, '-').toLowerCase();
    return (
      <div className="space-y-4">
        <div className="flex flex-wrap gap-3">
          <Button variant="secondary" onClick={() => copy(email.subject, 'email-subject')}>
            {copied === 'email-subject' ? 'Copied' : 'Copy Subject'}
          </Button>
          <Button variant="secondary" onClick={() => copy(`Subject: ${email.subject}\n\n${email.body}`, 'email-full')}>
            {copied === 'email-full' ? 'Copied' : 'Copy Email'}
          </Button>
          <Button variant="secondary" onClick={() => downloadText(filename, `Subject: ${email.subject}\n\n${email.body}`)}>
            Download
          </Button>
        </div>
        <Card>
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Subject</p>
          <p className="mt-2 text-sm font-medium text-ink">{email.subject}</p>
        </Card>
        <Card>
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Body</p>
          <div className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-ink">{email.body}</div>
        </Card>
      </div>
    );
  };

  const renderAssessment = (section: 'ats' | 'jdFit') => {
    if (!resumeDone || !resumeId) {
      return <NotGenerated label="A resume (required for ATS/JD-fit scoring)" />;
    }
    if (assessmentQuery.isLoading) {
      return <p className="text-sm text-ink-faint">Loading assessment…</p>;
    }
    if (assessmentQuery.isError || !assessmentQuery.data) {
      return (
        <Card>
          <p className="text-sm text-ink-muted">No assessment has been run yet for this resume.</p>
        </Card>
      );
    }
    const assessment = assessmentQuery.data;
    const skillReqs = assessment.requirementMatches.filter((m) => m.type === 'SKILL' || m.type === 'TECHNOLOGY');
    const skillsMatch = skillReqs.length > 0
      ? skillReqs.filter((m) => m.matchStrength !== 'NONE').length / skillReqs.length
      : null;

    if (section === 'ats') {
      return (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <ScoreCard label="ATS Compatibility" score={assessment.atsScore} />
            {skillsMatch !== null && <ScoreCard label="Skills Match" score={skillsMatch * 100} />}
          </div>
          <AtsChecklist checks={assessment.atsChecks} />
        </div>
      );
    }

    return (
      <div className="space-y-6">
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <ScoreCard label="Job Description Match" score={assessment.compatibilityScore * 100} />
          <ScoreCard label="Keyword Match" score={assessment.keywordMatch * 100} />
        </div>
        <JdFitBreakdown assessment={assessment} />
        <KeywordsPanel matched={assessment.matchedKeywords} missing={assessment.missingKeywords} />
        <RecommendationsList recommendations={assessment.recommendations} />
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-void">
      <AppHeader />
      <main className="mx-auto max-w-3xl px-6 py-12">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p className="text-sm font-medium uppercase tracking-wide text-ember-soft">Result</p>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight text-ink">
              {application.jobTitle ?? 'Your application'}
              {application.company ? ` — ${application.company}` : ''}
            </h1>
            <p className="mt-1 text-xs text-ink-faint">
              Generated {new Date(application.createdAt).toLocaleString()}
            </p>
          </div>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          {outputBadge('Resume', resumeDone, resumeFailed)}
          {outputBadge('Cover Letter', coverLetterDone, coverLetterFailed)}
          {outputBadge('Email', emailDone, emailFailed)}
          {anyFailed && (
            <span className="text-xs text-ink-faint">— generation failed for at least one output</span>
          )}
        </div>

        <div className="mt-8 flex flex-wrap gap-2 border-b border-border">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={`rounded-t-lg px-4 py-2 text-sm font-medium transition-colors ${
                tab === t.id
                  ? 'border-b-2 border-ember-soft text-ink'
                  : 'text-ink-faint hover:text-ink-muted'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        <div className="mt-6">
          {tab === 'resume' && renderResumeTab()}
          {tab === 'coverLetter' && renderCoverLetterTab()}
          {tab === 'email' && renderEmailTab()}
          {tab === 'ats' && renderAssessment('ats')}
          {tab === 'jdFit' && renderAssessment('jdFit')}
        </div>

        {tab === 'resume' && resumeQuery.data && (
          <div className="mt-8 space-y-4">
            <GroundingBanner grounding={resumeQuery.data.grounding} />
            <GapsList gaps={resumeQuery.data.gaps} />
            {resumeQuery.data.evidenceMatches.length > 0 && (
              <div>
                <p className="mb-2 text-xs font-medium uppercase tracking-wide text-ink-faint">
                  Requirement coverage
                </p>
                <EvidenceMatches matches={resumeQuery.data.evidenceMatches} />
              </div>
            )}
          </div>
        )}

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
