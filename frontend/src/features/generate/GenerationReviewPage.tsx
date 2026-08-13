import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { DashboardSidebar } from '@/features/dashboard/components/DashboardSidebar';
import type { GenerationType } from '@/services/applicationApi';
import { confirmJd, getAnalysis, getJd } from '@/services/jdApi';
import * as profileApi from '@/services/profileApi';
import { useSession } from '@/services/session';
import { ApplicationPackagePanel } from './components/ApplicationPackagePanel';
import { GenerationCta } from './components/GenerationCta';
import { GenerationProgress } from './components/GenerationProgress';
import { JobDescriptionPanel } from './components/JobDescriptionPanel';
import { RequirementsPanel } from './components/RequirementsPanel';
import { ReviewHeader } from './components/ReviewHeader';
import { SkillsAlignmentPanel } from './components/SkillsAlignmentPanel';
import { computeSkillsAlignment } from './utils/skillsAlignment';

interface ReviewCopy {
  pageTitle: string;
  subtitle: string;
  ctaHeading: string;
  ctaDescription: string;
  ctaLabel: string;
}

// RESUME_ONLY / ALL still pick a template on its own dedicated step (`TemplatePage`, via
// `/generate/template/:jdId`) rather than inline here — resume-service's template catalogue,
// custom-upload wizard and field-mapping editor are a whole self-contained surface, and this
// keeps that one real, already-battle-tested picker instead of a second copy of it. This page's
// own CTA for those two types is "Choose a template", which hands off to it with the JD and
// generation type already carried along; the *next* page's own CTA is what actually calls
// generation ("Generate my resume" / "Generate everything").
const COPY: Record<GenerationType, ReviewCopy> = {
  RESUME_ONLY: {
    pageTitle: 'Confirm and review',
    subtitle: 'Review your job match before generating your resume.',
    ctaHeading: 'Ready to choose a template?',
    ctaDescription: "Once you confirm your match, pick the resume template we'll generate your resume into.",
    ctaLabel: 'Choose a template',
  },
  COVER_LETTER_ONLY: {
    pageTitle: 'Confirm and review',
    subtitle: 'Review your job match before generating your cover letter.',
    ctaHeading: 'Ready to generate your cover letter?',
    ctaDescription:
      "Once you confirm, we'll generate a personalized cover letter using your profile and this job description.",
    ctaLabel: 'Generate my cover letter',
  },
  EMAIL_ONLY: {
    pageTitle: 'Confirm and review',
    subtitle: 'Review your job match before generating your email.',
    ctaHeading: 'Ready to generate your email?',
    ctaDescription:
      "Once you confirm, we'll generate a grounded application email using your profile and this job description.",
    ctaLabel: 'Generate my email',
  },
  ALL: {
    pageTitle: 'Confirm and review',
    subtitle: 'Review your job match before generating your complete application package.',
    ctaHeading: 'Ready to choose a template?',
    ctaDescription:
      "Pick the resume template for this package — your cover letter and email are grounded the same way and need no template.",
    ctaLabel: 'Choose a template',
  },
};

/**
 * The shared "career application review dashboard" (redesign brief) — one component behind
 * RESUME_ONLY / COVER_LETTER_ONLY / EMAIL_ONLY / ALL alike. Only copy, the generation-specific
 * summary section and the final CTA's destination vary by `generationType`; the JD, requirements
 * and skills-alignment sections are identical for every mode. Replaces the old narrow
 * `ReviewPage` with the same full-width layout across all four generation types.
 */
export function GenerationReviewPage() {
  const { jdId = '' } = useParams<{ jdId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const generationType = (searchParams.get('type') as GenerationType | null) ?? 'RESUME_ONLY';
  // Only relevant for RESUME_ONLY/ALL — set when arriving from the Templates page's "Use this
  // template" action, so TemplatePage (the next step) can preselect it instead of the user
  // having to pick it again. This page has no template UI of its own to preselect into.
  const preselectedTemplateId = searchParams.get('templateId');
  const templateParam = preselectedTemplateId ? `&templateId=${preselectedTemplateId}` : '';
  const needsTemplate = generationType === 'RESUME_ONLY' || generationType === 'ALL';
  const copy = COPY[generationType] ?? COPY.RESUME_ONLY;

  const { data: user } = useSession();

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

  // 'always' so returning from "Edit skills & experience" (a full route change to /profile and
  // back) always picks up whatever the user just added — see redesign spec &sect;12.
  const profileQuery = useQuery({
    queryKey: ['profile'],
    queryFn: profileApi.getProfile,
    refetchOnMount: 'always',
  });

  const alignment = useMemo(
    () => computeSkillsAlignment(analysisQuery.data?.requirements ?? [], profileQuery.data),
    [analysisQuery.data, profileQuery.data],
  );

  const canGenerate = isConfirmed && Boolean(analysisQuery.data);

  const handlePrimaryAction = () => {
    if (needsTemplate) {
      navigate(`/generate/template/${jdId}?type=${generationType}${templateParam}`);
      return;
    }
    navigate(`/generate/processing/${jdId}?type=${generationType}`);
  };

  if (!user || jdQuery.isLoading) {
    return <FullScreenSpinner label="Loading the job description…" />;
  }

  const displayName = user.displayName?.trim() || user.email;

  return (
    <div className="flex min-h-screen bg-void">
      <DashboardSidebar userName={displayName} userEmail={user.email} />

      <div className="flex min-w-0 flex-1 flex-col">
        <ReviewHeader title="Generate application" backTo={`/generate/job?type=${generationType}`} />

        <main className="min-w-0 flex-1 px-5 py-7 pl-16 sm:px-7 lg:px-10 lg:py-9 lg:pl-10">
          <div className="mx-auto max-w-[1680px]">
            <GenerationProgress activeStep={2} />

            <h1 className="mt-6 text-2xl font-semibold tracking-tight text-ink sm:text-[28px]">{copy.pageTitle}</h1>
            <p className="mt-1.5 text-sm text-ink-muted">{copy.subtitle}</p>

            {jdQuery.isError || !jdQuery.data ? (
              <div className="mt-8">
                <ErrorBanner error={jdQuery.error} />
              </div>
            ) : (
              <div className="mt-8 space-y-6">
                <div className="grid gap-6 lg:grid-cols-2">
                  <JobDescriptionPanel jd={jdQuery.data} />

                  {!isConfirmed ? (
                    <div className="flex flex-col items-start justify-center gap-4 rounded-2xl border border-dashed border-border-strong bg-surface/60 p-6 sm:p-8">
                      <div>
                        <h2 className="text-base font-semibold text-ink">Confirm this is the right job</h2>
                        <p className="mt-1.5 text-sm text-ink-muted">
                          Nothing is generated until you confirm this is the job you're applying to — once confirmed,
                          we'll extract its requirements and grade them against your profile.
                        </p>
                      </div>
                      {confirmMutation.isError && <ErrorBanner error={confirmMutation.error} />}
                      <Button onClick={() => confirmMutation.mutate()} loading={confirmMutation.isPending}>
                        Confirm this is correct
                      </Button>
                    </div>
                  ) : analysisQuery.isLoading ? (
                    <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-border bg-surface p-8 text-center text-sm text-ink-muted">
                      <span
                        className="h-5 w-5 animate-spin rounded-full border-2 border-current border-t-transparent"
                        aria-hidden="true"
                      />
                      Analysing requirements against the job description…
                    </div>
                  ) : analysisQuery.isError ? (
                    <div className="rounded-2xl border border-border bg-surface p-6 sm:p-8">
                      <ErrorBanner error={analysisQuery.error} />
                    </div>
                  ) : analysisQuery.data ? (
                    <RequirementsPanel analysis={analysisQuery.data} />
                  ) : null}
                </div>

                {isConfirmed && analysisQuery.data && (
                  <>
                    <SkillsAlignmentPanel items={alignment} profile={profileQuery.data} />

                    {generationType === 'ALL' && <ApplicationPackagePanel />}

                    <GenerationCta
                      heading={copy.ctaHeading}
                      description={copy.ctaDescription}
                      ctaLabel={copy.ctaLabel}
                      onGenerate={handlePrimaryAction}
                      disabled={!canGenerate}
                    />
                  </>
                )}
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
