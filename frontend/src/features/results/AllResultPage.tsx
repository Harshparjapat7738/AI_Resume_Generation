import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { DashboardShell } from '@/features/dashboard/components/DashboardShell';
import { CopyIcon, DownloadIcon } from '@/features/dashboard/icons';
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
import { contentAsPlainText, ResumeContentView } from './components/ResumeContentView';
import { DownloadingOverlay } from './components/DownloadingOverlay';
import { GapsList } from './components/GapsList';
import { GroundingBanner } from './components/GroundingBanner';
import { JdFitBreakdown } from './components/JdFitBreakdown';
import { KeywordsPanel } from './components/KeywordsPanel';
import { NextSteps } from './components/NextSteps';
import { RecommendationsList } from './components/RecommendationsList';
import { RequirementCoverage } from './components/RequirementCoverage';
import { ResultTopBar } from './components/ResultTopBar';
import { ScoreOverview } from './components/ScoreOverview';
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

function ActionButton({
  icon,
  label,
  onClick,
  variant = 'secondary',
}: {
  icon: ReactNode;
  label: string;
  onClick: () => void;
  variant?: 'primary' | 'secondary';
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        variant === 'primary'
          ? 'flex items-center gap-2 rounded-full bg-linear-to-r from-ember to-rose px-5 py-2.5 text-sm font-semibold text-void transition-opacity hover:opacity-90'
          : 'flex items-center gap-2 rounded-full border border-border-strong px-4 py-2.5 text-sm font-medium text-ink-muted transition-colors hover:border-ink-muted hover:text-ink'
      }
    >
      {icon}
      {label}
    </button>
  );
}

/**
 * "Generate All" result page — one `Application` (`GenerationType.ALL`), three independently
 * tracked outputs. Same `DashboardShell`/`ResultTopBar`/`DownloadingOverlay`/`NextSteps` shell
 * as `ResultPage`/`CoverLetterResultPage`/`EmailResultPage` for UI parity across every result
 * page, and the ATS/JD-Fit tabs now reuse the same `ScoreOverview`/`RequirementCoverage`
 * components the resume-only page uses instead of a separate, older score-card layout. Every
 * query/mutation driving the actual data — `getApplication`, the three output queries, the
 * assessment query, `retryResume`/`retryCoverLetter`/`retryEmail`, `preparePdf` — is otherwise
 * unchanged: only presentation and the download flow's animation/redirect are new. Every
 * piece of state driving this page still lives on the `Application` itself, so a refresh
 * re-fetches the same picture rather than losing it.
 */
export function AllResultPage() {
  const { applicationId = '' } = useParams<{ applicationId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>('resume');
  const [copied, setCopied] = useState<string | null>(null);
  const [downloadStage, setDownloadStage] = useState<'idle' | 'downloading' | 'done'>('idle');
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
      setDownloadStage('done');
    },
    onError: () => setDownloadStage('idle'),
  });

  const downloadResumePdf = () => {
    setDownloadStage('downloading');
    preparePdf.mutate();
  };

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

  // Same "downloading" pacing as CoverLetterResultPage/EmailResultPage — a real, already-saved
  // client-side text file, paused briefly only so the animation matches the resume PDF's.
  const downloadText = (filename: string, text: string) => {
    setDownloadStage('downloading');
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = window.document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
    setTimeout(() => setDownloadStage('done'), 500);
  };

  if (applicationQuery.isLoading) {
    return <FullScreenSpinner label="Loading your application…" />;
  }

  if (applicationQuery.isError || !application) {
    return (
      <DashboardShell>
        {({ user, onLogout }) => (
          <>
            <ResultTopBar user={user} onLogout={onLogout} />
            <div className="mt-6">
              <ErrorBanner error={applicationQuery.error ?? new Error('Application not found')} />
              <Link to="/generate" className="mt-4 inline-block text-sm text-ink-muted hover:text-ink">
                Start a new application
              </Link>
            </div>
          </>
        )}
      </DashboardShell>
    );
  }

  const resumeDone = Boolean(application.resumeVersionId);
  const resumeFailed = Boolean(application.resumeError);
  const coverLetterDone = Boolean(application.coverLetterVersionId);
  const coverLetterFailed = Boolean(application.coverLetterError);
  const emailDone = Boolean(application.emailId);
  const emailFailed = Boolean(application.emailError);

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
          <ActionButton
            icon={<CopyIcon className="h-4 w-4" />}
            label={copied === 'resume' ? 'Copied' : copied === 'resume-failed' ? "Couldn't copy" : 'Copy text'}
            onClick={() => resumeQuery.data && copy(contentAsPlainText(resumeQuery.data.content), 'resume')}
          />
          <ActionButton
            icon={<DownloadIcon className="h-4 w-4" />}
            label={preparePdf.isPending ? 'Preparing…' : 'Download Resume PDF'}
            variant="primary"
            onClick={downloadResumePdf}
          />
        </div>
        {preparePdf.isError && (
          <p className="text-xs text-ember-soft">PDF unavailable for this generation. Try downloading again.</p>
        )}
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
          <ActionButton
            icon={<CopyIcon className="h-4 w-4" />}
            label={copied === 'cover-letter' ? 'Copied' : copied === 'cover-letter-failed' ? "Couldn't copy" : 'Copy'}
            onClick={() => copy(text, 'cover-letter')}
          />
          <ActionButton
            icon={<DownloadIcon className="h-4 w-4" />}
            label="Download"
            variant="primary"
            onClick={() => downloadText(filename, text)}
          />
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
          <ActionButton
            icon={<CopyIcon className="h-4 w-4" />}
            label={copied === 'email-subject' ? 'Copied' : 'Copy Subject'}
            onClick={() => copy(email.subject, 'email-subject')}
          />
          <ActionButton
            icon={<CopyIcon className="h-4 w-4" />}
            label={copied === 'email-full' ? 'Copied' : 'Copy Email'}
            onClick={() => copy(`Subject: ${email.subject}\n\n${email.body}`, 'email-full')}
          />
          <ActionButton
            icon={<DownloadIcon className="h-4 w-4" />}
            label="Download"
            variant="primary"
            onClick={() => downloadText(filename, `Subject: ${email.subject}\n\n${email.body}`)}
          />
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
          <ScoreOverview assessment={assessment} skillsMatch={skillsMatch} />
          <AtsChecklist checks={assessment.atsChecks} limit={5} />
        </div>
      );
    }

    return (
      <div className="space-y-6">
        <JdFitBreakdown assessment={assessment} />
        <KeywordsPanel matched={assessment.matchedKeywords} missing={assessment.missingKeywords} />
        <RecommendationsList recommendations={assessment.recommendations} />
      </div>
    );
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
              <p className="text-xs font-semibold uppercase tracking-wide text-ember-soft">Result</p>
              <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
                {application.jobTitle ?? 'Your application'}
                {application.company ? <span className="text-ink-faint"> — {application.company}</span> : null}
              </h1>
              <p className="mt-2 text-sm text-ink-faint">Generated {new Date(application.createdAt).toLocaleString()}</p>
              <div className="mt-4 flex flex-wrap items-center gap-2">
                {outputBadge('Resume', resumeDone, resumeFailed)}
                {outputBadge('Cover Letter', coverLetterDone, coverLetterFailed)}
                {outputBadge('Email', emailDone, emailFailed)}
              </div>
            </Card>

            <div className="flex flex-wrap gap-2 border-b border-border">
              {TABS.map((t) => (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => setTab(t.id)}
                  className={`rounded-t-lg px-4 py-2 text-sm font-medium transition-colors ${
                    tab === t.id ? 'border-b-2 border-ember-soft text-ink' : 'text-ink-faint hover:text-ink-muted'
                  }`}
                >
                  {t.label}
                </button>
              ))}
            </div>

            {tab === 'resume' && renderResumeTab()}
            {tab === 'coverLetter' && renderCoverLetterTab()}
            {tab === 'email' && renderEmailTab()}
            {tab === 'ats' && renderAssessment('ats')}
            {tab === 'jdFit' && renderAssessment('jdFit')}

            {tab === 'resume' && resumeQuery.data && (
              <>
                <GroundingBanner grounding={resumeQuery.data.grounding} />
                <GapsList gaps={resumeQuery.data.gaps} />
                <RequirementCoverage matches={resumeQuery.data.evidenceMatches} />
              </>
            )}

            <NextSteps />
          </div>
        </>
      )}
    </DashboardShell>
  );
}
