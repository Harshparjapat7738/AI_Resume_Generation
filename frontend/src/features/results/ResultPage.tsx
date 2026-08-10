import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AppHeader } from '@/components/layout/AppHeader';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { ApiError } from '@/services/apiClient';
import { assessResume, getAssessment } from '@/services/assessmentApi';
import { downloadDocument, getRenderedDocument, renderResumePdf, type RenderedDocument } from '@/services/documentApi';
import { generateResume, getResume, type ResumeVersion } from '@/services/resumeApi';
import { getTemplate } from '@/services/templateApi';
import { AtsChecklist } from './components/AtsChecklist';
import { EvidenceMatches } from './components/EvidenceMatches';
import { GapsList } from './components/GapsList';
import { GroundingBanner } from './components/GroundingBanner';
import { JdFitBreakdown } from './components/JdFitBreakdown';
import { KeywordsPanel } from './components/KeywordsPanel';
import { RecommendationsList } from './components/RecommendationsList';
import { contentAsPlainText, ResumeContentView } from './components/ResumeContentView';
import { ScoreCard } from './components/ScoreCard';

function pdfFilename(resume: ResumeVersion): string {
  return `resume-${resume.jobTitle ?? resume.id}.pdf`.replace(/\s+/g, '-').toLowerCase();
}

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

  const regenerate = useMutation({
    mutationFn: ({ jobDescriptionId, templateId }: { jobDescriptionId: string; templateId: string | null }) =>
      generateResume(jobDescriptionId, templateId ?? undefined),
    onSuccess: (resume) => navigate(`/results/${resume.id}`, { replace: true }),
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
    },
  });

  if (resumeQuery.isLoading) {
    return <FullScreenSpinner label="Loading your result…" />;
  }

  if (resumeQuery.isError || !resumeQuery.data) {
    return (
      <div className="min-h-screen bg-void">
        <AppHeader />
        <main className="mx-auto max-w-2xl px-6 py-12">
          <ErrorBanner error={resumeQuery.error ?? new Error('Result not found')} />
          <Link to="/generate" className="mt-4 inline-block text-sm text-ink-muted hover:text-ink">
            Start a new application
          </Link>
        </main>
      </div>
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
    <div className="min-h-screen bg-void">
      <AppHeader />
      <main className="mx-auto max-w-3xl px-6 py-12">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p className="text-sm font-medium uppercase tracking-wide text-ember-soft">Result</p>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight text-ink">
              {resume.jobTitle ?? 'Your grounded resume'}
              {resume.company ? ` — ${resume.company}` : ''}
            </h1>
            <p className="mt-1 text-xs text-ink-faint">
              Generated {new Date(resume.createdAt).toLocaleString()}
              {templateQuery.data ? ` — ${templateQuery.data.name} template` : ''}
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button variant="secondary" onClick={handleCopy}>
              {copied ? 'Copied' : copyFailed ? "Couldn't copy" : 'Copy text'}
            </Button>
            <Button onClick={() => preparePdf.mutate()} loading={preparePdf.isPending}>
              Download Resume PDF
            </Button>
            <Button
              variant="secondary"
              onClick={() => regenerate.mutate({ jobDescriptionId: resume.jobDescriptionId, templateId: resume.templateId })}
              loading={regenerate.isPending}
            >
              Regenerate
            </Button>
          </div>
        </div>

        {preparePdf.isError && (
          <p className="mt-2 text-xs text-ember-soft">
            PDF unavailable for this older generation.
          </p>
        )}

        {pdfBlobUrl && !preparePdf.isError && (
          <Card className="mt-4 !p-0 overflow-hidden">
            <iframe
              title="Resume PDF preview"
              src={pdfBlobUrl}
              className="h-[600px] w-full rounded-2xl border-0"
            />
          </Card>
        )}

        {regenerate.isError && (
          <div className="mt-4">
            <ErrorBanner error={regenerate.error} />
          </div>
        )}

        {/* ATS / JD-fit dashboard */}
        <div className="mt-8">
          {assessmentQuery.isLoading && (
            <p className="text-sm text-ink-faint">Loading assessment…</p>
          )}
          {assessmentMissing && (
            <div className="rounded-2xl border border-border bg-surface p-6 text-center">
              <p className="text-sm text-ink-muted">No ATS assessment has been run for this resume yet.</p>
              <Button className="mt-3" onClick={() => runAssessment.mutate()} loading={runAssessment.isPending}>
                Run ATS analysis
              </Button>
              {runAssessment.isError && (
                <div className="mt-3">
                  <ErrorBanner error={runAssessment.error} />
                </div>
              )}
            </div>
          )}
          {assessmentQuery.isError && !assessmentMissing && <ErrorBanner error={assessmentQuery.error} />}

          {assessment && (
            <div className="space-y-6">
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                <ScoreCard label="ATS Compatibility" score={assessment.atsScore} />
                <ScoreCard label="Job Description Match" score={assessment.compatibilityScore * 100} />
                <ScoreCard label="Keyword Match" score={assessment.keywordMatch * 100} />
                {skillsMatch !== null && <ScoreCard label="Skills Match" score={skillsMatch * 100} />}
              </div>

              <JdFitBreakdown assessment={assessment} />
              <AtsChecklist checks={assessment.atsChecks} />
              <KeywordsPanel matched={assessment.matchedKeywords} missing={assessment.missingKeywords} />
              <RecommendationsList recommendations={assessment.recommendations} />
            </div>
          )}
        </div>

        <div className="mt-8 space-y-4">
          <GroundingBanner grounding={resume.grounding} />

          {resume.removedSections.length > 0 && (
            <div className="rounded-xl border border-ember/30 bg-ember/10 px-4 py-3 text-sm text-ink">
              {resume.removedSections.length} statement{resume.removedSections.length === 1 ? '' : 's'} couldn't be
              verified after a retry and were removed rather than shown unverified.
            </div>
          )}

          <Card>
            <ResumeContentView content={resume.content} />
          </Card>

          <GapsList gaps={resume.gaps} />

          {resume.evidenceMatches.length > 0 && (
            <div>
              <p className="mb-2 text-xs font-medium uppercase tracking-wide text-ink-faint">
                Requirement coverage
              </p>
              <EvidenceMatches matches={resume.evidenceMatches} />
            </div>
          )}
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
