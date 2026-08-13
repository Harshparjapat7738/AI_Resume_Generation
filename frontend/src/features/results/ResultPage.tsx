import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { DashboardShell } from '@/features/dashboard/components/DashboardShell';
import { ApiError } from '@/services/apiClient';
import { assessResume, getAssessment } from '@/services/assessmentApi';
import { downloadDocument, getRenderedDocument, renderResumePdf, type RenderedDocument } from '@/services/documentApi';
import { getProfile } from '@/services/profileApi';
import { generateResume, getResume, type ResumeVersion } from '@/services/resumeApi';
import { getTemplate } from '@/services/templateApi';
import { AtsChecklist } from './components/AtsChecklist';
import { contentAsPlainText } from './components/ResumeContentView';
import { DownloadingOverlay } from './components/DownloadingOverlay';
import { ExperienceEvidence } from './components/ExperienceEvidence';
import { GapsList } from './components/GapsList';
import { GroundingBanner } from './components/GroundingBanner';
import { JdFitBreakdown } from './components/JdFitBreakdown';
import { KeywordsPanel } from './components/KeywordsPanel';
import { MissingKeywordsPanel } from './components/MissingKeywordsPanel';
import { NextSteps } from './components/NextSteps';
import { RecommendationsList } from './components/RecommendationsList';
import { RegenerationStatus, type RegenerationStage } from './components/RegenerationStatus';
import { RequirementCoverage } from './components/RequirementCoverage';
import { ResultHero } from './components/ResultHero';
import { ResultTopBar } from './components/ResultTopBar';
import { ScoreOverview } from './components/ScoreOverview';

function pdfFilename(resume: ResumeVersion): string {
  return `resume-${resume.jobTitle ?? resume.id}.pdf`.replace(/\s+/g, '-').toLowerCase();
}

/**
 * Dashboard-shell layout for the resume result/analysis page, plus the "Add Missing Keywords →
 * Update Profile → Regenerate Resume" workflow (`MissingKeywordsPanel`/
 * `AddSkillToProfileModal`/`RegenerationStatus`). Every query/mutation/handler that predates
 * that workflow (resume, assessment, template, rendered-document, copy/download) is unchanged;
 * `regenerate` itself now also re-runs the ATS/JD assessment for the new version and tracks a
 * real pipeline stage (see its own comment below) — the one behavioral change here, everything
 * else is presentation only, following the sidebar + wide multi-column dashboard direction the
 * rest of the authenticated app (Dashboard, Templates, Applications, …) already uses via
 * `DashboardShell`/`PageHeader`, rather than the old `AppHeader` + narrow centered column this
 * page alone still had.
 */
export function ResultPage() {
  const { resumeId = '' } = useParams<{ resumeId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [copied, setCopied] = useState(false);
  const [copyFailed, setCopyFailed] = useState(false);

  const resumeQuery = useQuery({
    queryKey: ['resume', resumeId],
    queryFn: () => getResume(resumeId),
    enabled: Boolean(resumeId),
  });

  const assessmentQuery = useQuery({
    queryKey: ['assessment', resumeId],
    queryFn: () => getAssessment(resumeId),
    enabled: Boolean(resumeId),
    retry: false,
  });

  const runAssessment = useMutation({
    mutationFn: () => assessResume(resumeId),
    onSuccess: (assessment) => queryClient.setQueryData(['assessment', resumeId], assessment),
  });

  // Same query key/fn ProfilePage and DashboardPage already use — shares their cache rather
  // than issuing a separate fetch, and a profile edit made anywhere is reflected everywhere.
  const profileQuery = useQuery({ queryKey: ['profile'], queryFn: getProfile });

  // "Regenerate Resume" (redesign spec &sect;6/11/13) — reuses the exact same
  // jobDescriptionId + templateId generateResume already used before this workflow existed, so
  // it's still the same resume "thread" (this page has never had a separate applicationId of
  // its own to preserve — see resumeApi/ResumeVersion — jobDescriptionId + templateId is the
  // grouping key this flow already relies on). The only change from the previous version of
  // this mutation: it also re-runs the ATS/JD assessment for the new version (the same
  // generate-then-assess pairing AllResultPage's regenerate flow already uses) and tracks a
  // real, honest pipeline stage for the status strip instead of just a boolean pending flag.
  const [regenStage, setRegenStage] = useState<RegenerationStage>('idle');
  const [regenErrorMessage, setRegenErrorMessage] = useState<string | null>(null);

  const regenerate = useMutation({
    mutationFn: async ({ jobDescriptionId, templateId }: { jobDescriptionId: string; templateId: string | null }) => {
      setRegenErrorMessage(null);
      setRegenStage('generating');
      let resume: ResumeVersion;
      try {
        resume = await generateResume(jobDescriptionId, templateId ?? undefined);
      } catch (error) {
        setRegenStage('failed');
        setRegenErrorMessage(error instanceof ApiError ? error.body.message : 'Could not reach the server. Try again.');
        throw error;
      }
      setRegenStage('assessing');
      let assessment;
      try {
        assessment = await assessResume(resume.id);
      } catch {
        // Non-fatal — same as every other resume flow (initial generation included): the
        // resume itself generated fine, ATS analysis just hasn't run yet and the "Run ATS
        // analysis" fallback already on this page still covers it.
      }
      setRegenStage('done');
      return { resume, assessment };
    },
    onSuccess: ({ resume, assessment }) => {
      queryClient.setQueryData(['resume', resume.id], resume);
      if (assessment) queryClient.setQueryData(['assessment', resume.id], assessment);
      // The old preview PDF belongs to the previous version's content — must not linger and
      // look like it matches the regenerated resume.
      setPdfBlobUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return null;
      });
      navigate(`/results/${resume.id}`, { replace: true });
    },
  });

  const templateQuery = useQuery({
    queryKey: ['template', resumeQuery.data?.templateId],
    queryFn: () => getTemplate(resumeQuery.data!.templateId!),
    enabled: Boolean(resumeQuery.data?.templateId),
    retry: false,
  });

  // A resume version generated before document-service existed has nothing to fetch here —
  // 404 is expected and not an error state; see the "PDF unavailable" fallback below.
  const renderedDocumentQuery = useQuery({
    queryKey: ['rendered-document', resumeId],
    queryFn: () => getRenderedDocument(resumeId),
    enabled: Boolean(resumeId),
    retry: false,
  });

  const [pdfBlobUrl, setPdfBlobUrl] = useState<string | null>(null);
  const pdfBlobUrlRef = useRef<string | null>(null);
  useEffect(() => {
    pdfBlobUrlRef.current = pdfBlobUrl;
  }, [pdfBlobUrl]);
  useEffect(() => () => {
    if (pdfBlobUrlRef.current) URL.revokeObjectURL(pdfBlobUrlRef.current);
  }, []);

  // Drives `DownloadingOverlay` (redesign: "download animation → return to dashboard"). The
  // real download itself — render/fetch the blob, trigger the browser's save — is entirely
  // unchanged below; this only tracks a two-stage UI state around that same mutation:
  // "downloading" while it's pending, "done" for a brief confirmation once the file has
  // actually been handed to the browser, before returning to the dashboard. Never set (and
  // therefore never shown) on failure — `preparePdf.isError` falls straight through to the
  // existing "PDF unavailable" banner instead.
  const [downloadComplete, setDownloadComplete] = useState(false);

  const preparePdf = useMutation({
    mutationFn: async (): Promise<{ document: RenderedDocument; blob: Blob }> => {
      const existing = renderedDocumentQuery.data ?? await renderResumePdf(resumeId);
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
      link.download = pdfFilename(resumeQuery.data!);
      link.click();
      setDownloadComplete(true);
    },
  });

  if (resumeQuery.isLoading) {
    return <FullScreenSpinner label="Loading your result…" />;
  }

  if (resumeQuery.isError || !resumeQuery.data) {
    return (
      <DashboardShell>
        {({ user, onLogout }) => (
          <>
            <ResultTopBar user={user} onLogout={onLogout} />
            <div className="mt-6">
              <ErrorBanner error={resumeQuery.error ?? new Error('Result not found')} />
              <Link to="/generate" className="mt-4 inline-block text-sm text-ink-muted hover:text-ink">
                Start a new application
              </Link>
            </div>
          </>
        )}
      </DashboardShell>
    );
  }

  const resume = resumeQuery.data;
  const assessment = assessmentQuery.data;
  const assessmentMissing = assessmentQuery.isError && assessmentQuery.error instanceof ApiError
    && assessmentQuery.error.status === 404;

  const skillReqs = assessment?.requirementMatches.filter((m) => m.type === 'SKILL' || m.type === 'TECHNOLOGY') ?? [];
  const skillsMatch = skillReqs.length > 0
    ? skillReqs.filter((m) => m.matchStrength !== 'NONE').length / skillReqs.length
    : null;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(contentAsPlainText(resume.content));
    } catch {
      setCopyFailed(true);
      setTimeout(() => setCopyFailed(false), 2000);
      return;
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <DashboardShell>
      {({ user, onLogout }) => (
        <>
          {(preparePdf.isPending || downloadComplete) && (
            <DownloadingOverlay
              stage={downloadComplete ? 'done' : 'downloading'}
              onGoToDashboard={() => navigate('/dashboard')}
            />
          )}

          <ResultTopBar user={user} onLogout={onLogout} />

          <div className="mt-6 space-y-6">
            <ResultHero
              jobTitle={resume.jobTitle ?? 'Your grounded resume'}
              company={resume.company}
              createdAt={resume.createdAt}
              templateName={templateQuery.data?.name ?? null}
              assessment={assessment}
              onCopy={handleCopy}
              copyLabel={copied ? 'Copied' : copyFailed ? "Couldn't copy" : 'Copy text'}
              onDownload={() => {
                setDownloadComplete(false);
                preparePdf.mutate();
              }}
              downloadPending={preparePdf.isPending}
              downloadFailed={preparePdf.isError}
              onRegenerate={() => regenerate.mutate({ jobDescriptionId: resume.jobDescriptionId, templateId: resume.templateId })}
              regeneratePending={regenerate.isPending}
            />

            <RegenerationStatus
              stage={regenStage}
              error={regenErrorMessage}
              onRetry={() => regenerate.mutate({ jobDescriptionId: resume.jobDescriptionId, templateId: resume.templateId })}
            />

            {pdfBlobUrl && !preparePdf.isError && (
              <Card className="!p-0 overflow-hidden">
                <iframe title="Resume PDF preview" src={pdfBlobUrl} className="h-[600px] w-full rounded-2xl border-0" />
              </Card>
            )}

            {assessmentQuery.isLoading && <p className="text-sm text-ink-faint">Loading assessment…</p>}

            {assessmentMissing && (
              <div className="rounded-2xl border border-border bg-surface p-8 text-center">
                <p className="text-sm text-ink-muted">No ATS assessment has been run for this resume yet.</p>
                <Button className="mt-4" onClick={() => runAssessment.mutate()} loading={runAssessment.isPending}>
                  Run ATS analysis
                </Button>
                {runAssessment.isError && (
                  <div className="mt-4">
                    <ErrorBanner error={runAssessment.error} />
                  </div>
                )}
              </div>
            )}
            {assessmentQuery.isError && !assessmentMissing && <ErrorBanner error={assessmentQuery.error} />}

            {assessment && (
              <>
                <ScoreOverview assessment={assessment} skillsMatch={skillsMatch} />

                <JdFitBreakdown assessment={assessment} />

                {/* Grounding status — compact, full-width strip (point 11). */}
                <div className="space-y-3">
                  <GroundingBanner grounding={resume.grounding} />
                  {resume.removedSections.length > 0 && (
                    <div className="rounded-xl border border-ember/30 bg-ember/10 px-4 py-3 text-sm text-ink">
                      {resume.removedSections.length} statement{resume.removedSections.length === 1 ? '' : 's'}{' '}
                      couldn't be verified after a retry and were removed rather than shown unverified.
                    </div>
                  )}
                </div>

                {/* Main analysis dashboard grid (point 7): ATS breakdown and Experience both
                    run tall (two rows), Keywords and Recommendations share the middle column. */}
                <div className="grid grid-cols-1 gap-6 lg:grid-cols-3 lg:items-start">
                  <div className="lg:row-span-2">
                    <AtsChecklist checks={assessment.atsChecks} limit={5} />
                  </div>
                  {/* Missing keywords now get their own richer, actionable panel below (the
                      "Add Missing Keywords" workflow) — this cell keeps only the matched
                      side, which `KeywordsPanel` already renders correctly on its own when
                      `missing` is empty. */}
                  <KeywordsPanel matched={assessment.matchedKeywords} missing={[]} />
                  <div className="lg:row-span-2 space-y-6">
                    {resume.content.summary && (
                      <div className="rounded-2xl border border-border bg-surface p-6">
                        <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Summary</p>
                        <p className="mt-2 text-sm leading-relaxed text-ink">{resume.content.summary.text}</p>
                      </div>
                    )}
                    <ExperienceEvidence experienceBullets={resume.content.experienceBullets} />
                  </div>
                  <RecommendationsList recommendations={assessment.recommendations} />
                </div>

                <MissingKeywordsPanel
                  missingKeywords={assessment.missingKeywords}
                  profile={profileQuery.data}
                  profileLoading={profileQuery.isLoading}
                  onRegenerate={() => regenerate.mutate({ jobDescriptionId: resume.jobDescriptionId, templateId: resume.templateId })}
                  regenerating={regenerate.isPending}
                />

                <GapsList gaps={resume.gaps} />

                <RequirementCoverage matches={resume.evidenceMatches} />
              </>
            )}

            <NextSteps />
          </div>
        </>
      )}
    </DashboardShell>
  );
}
