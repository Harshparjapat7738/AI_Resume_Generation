import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { showToast } from '@/components/ui/toast';
import { DashboardSidebar } from '@/features/dashboard/components/DashboardSidebar';
import { confirmJd, editJd, getAnalysis, getJd } from '@/services/jdApi';
import * as profileApi from '@/services/profileApi';
import { useSession } from '@/services/session';
import { ConfirmAnalysisModal } from './components/ConfirmAnalysisModal';
import { GenerationCta } from './components/GenerationCta';
import { GenerationProgress, stepsForGenerationType } from './components/GenerationProgress';
import { JobDescriptionPanel } from './components/JobDescriptionPanel';
import { ReviewHeader } from './components/ReviewHeader';
import { UnsavedChangesDialog } from './components/UnsavedChangesDialog';
import { computeSkillsAlignment } from './utils/skillsAlignment';

interface ReviewCopy {
  pageTitle: string;
  subtitle: string;
  ctaHeading: string;
  ctaDescription: string;
  ctaLabel: string;
}

// RESUME_ONLY / ALL still pick a template on its own dedicated step (`TemplatePage`, via
// its own step) rather than inline here — resume-service's template catalogue,
// custom-upload wizard and field-mapping editor are a whole self-contained surface, and this
// keeps that one real, already-battle-tested picker instead of a second copy of it. This page's
// own CTA for those two types is "Choose a template", which hands off to it with the JD and
// generation type already carried along; the *next* page's own CTA is what actually calls
// generation ("Generate my resume" / "Generate everything").
const COPY: Record<string, ReviewCopy> = {
  JD_OPTIMIZATION: {
    pageTitle: 'Confirm and review',
    subtitle: 'Review your job match before generating your JD optimization.',
    ctaHeading: 'Ready to generate your JD optimization?',
    ctaDescription:
      "Once you confirm, we'll match your verified profile against this job description and give you the keywords, matches and gaps to build your documents with.",
    ctaLabel: 'Generate JD Optimization',
  },
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
  const generationType = searchParams.get('type') ?? 'JD_OPTIMIZATION';
  const copy: ReviewCopy = COPY[generationType] ?? COPY.JD_OPTIMIZATION!;

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

  // --- Edit JD (redesign brief) --------------------------------------------------------------
  const [isEditingJd, setIsEditingJd] = useState(false);
  const [jdDraft, setJdDraft] = useState('');
  const [showUnsavedDialog, setShowUnsavedDialog] = useState(false);
  const [pendingLeaveAction, setPendingLeaveAction] = useState<(() => void) | null>(null);

  const hasUnsavedJdChanges = isEditingJd && jdDraft !== (jdQuery.data?.rawText ?? '');

  const editJdMutation = useMutation({
    mutationFn: (text: string) => editJd(jdId, text),
    onSuccess: (updated) => {
      queryClient.setQueryData(['jd', jdId], updated);
      setIsEditingJd(false);
      showToast('Job description updated.');
    },
  });

  const startEdit = () => {
    setJdDraft(jdQuery.data?.rawText ?? '');
    setIsEditingJd(true);
  };

  // Runs `action` immediately unless there's an unsaved JD edit in flight, in which case it's
  // deferred until the Unsaved Changes dialog resolves — the same guard behind Back, Save draft,
  // Cancel and Confirm alike (redesign brief: "if... user tries to leave/cancel/confirm").
  const requestLeave = (action: () => void) => {
    if (hasUnsavedJdChanges) {
      setPendingLeaveAction(() => action);
      setShowUnsavedDialog(true);
      return;
    }
    action();
  };

  const handleCancelEdit = () => requestLeave(() => setIsEditingJd(false));

  const closeUnsavedDialog = () => {
    setShowUnsavedDialog(false);
    setPendingLeaveAction(null);
  };

  const discardAndContinue = () => {
    setIsEditingJd(false);
    closeUnsavedDialog();
    pendingLeaveAction?.();
  };

  const saveAndContinue = () => {
    editJdMutation.mutate(jdDraft, {
      onSuccess: () => {
        closeUnsavedDialog();
        pendingLeaveAction?.();
      },
    });
  };

  // A native "are you sure you want to leave?" prompt for a browser-level navigation (refresh,
  // tab close, typed URL) — the in-app dialog above only intercepts Back/Save draft/Confirm.
  useEffect(() => {
    if (!hasUnsavedJdChanges) return;
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [hasUnsavedJdChanges]);

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

  // --- Post-confirm skill-gap popup ----------------------------------------------------------
  // Opened only by a fresh Confirm click (below) — never on a reload of an already-confirmed
  // JD, so a returning user isn't re-prompted every time they revisit this page. The full
  // requirements/skills-alignment breakdown still renders on the page underneath regardless;
  // this is a faster first look, not a replacement for it.
  const [showAnalysisModal, setShowAnalysisModal] = useState(false);
  const missingAlignmentItems = useMemo(
    () => alignment.filter((item) => item.status === 'MISSING' && item.keyword),
    [alignment],
  );

  // If analysis itself fails, there's nothing to show in the popup — close it and let the
  // existing inline error banner (below) carry the failure instead of stacking a second one.
  useEffect(() => {
    if (analysisQuery.isError) setShowAnalysisModal(false);
  }, [analysisQuery.isError]);

  // Straight to processing: neither remaining flow (JD optimization, email) picks a template,
  // so the template step that used to sit between here and there is gone (ADR-033).
  const handlePrimaryAction = () => navigate(`/generate/processing/${jdId}?type=${generationType}`);

  const handleBack = () => requestLeave(() => navigate(`/generate/job?type=${generationType}`));
  const handleSaveDraft = () => requestLeave(() => navigate('/dashboard'));
  const handleConfirmClick = () =>
    requestLeave(() => confirmMutation.mutate(undefined, { onSuccess: () => setShowAnalysisModal(true) }));

  if (!user || jdQuery.isLoading) {
    return <FullScreenSpinner label="Loading the job description…" />;
  }

  const displayName = user.displayName?.trim() || user.email;

  return (
    <div className="flex min-h-screen bg-void">
      <DashboardSidebar userName={displayName} userEmail={user.email} />

      <div className="flex min-w-0 flex-1 flex-col">
        <ReviewHeader title="Generate application" onBack={handleBack} onSaveDraft={handleSaveDraft} />

        <main className="min-w-0 flex-1 px-5 py-7 pl-16 sm:px-7 lg:px-10 lg:py-9 lg:pl-10">
          <div className="mx-auto max-w-[1680px]">
            <GenerationProgress activeStep={2} steps={stepsForGenerationType(generationType)} />

            <h1 className="mt-6 text-2xl font-semibold tracking-tight text-ink sm:text-[28px]">{copy.pageTitle}</h1>
            <p className="mt-1.5 text-sm text-ink-muted">{copy.subtitle}</p>

            {jdQuery.isError || !jdQuery.data ? (
              <div className="mt-8">
                <ErrorBanner error={jdQuery.error} />
              </div>
            ) : (
              <div className="mt-8 space-y-6">
                <JobDescriptionPanel
                  jd={jdQuery.data}
                  editable={!isConfirmed}
                  isEditing={isEditingJd}
                  draftText={jdDraft}
                  onDraftChange={setJdDraft}
                  onStartEdit={startEdit}
                  onCancelEdit={handleCancelEdit}
                  onSave={() => editJdMutation.mutate(jdDraft)}
                  saving={editJdMutation.isPending}
                  saveError={editJdMutation.error}
                  isConfirmed={isConfirmed}
                  onConfirm={handleConfirmClick}
                  confirming={confirmMutation.isPending}
                  confirmError={confirmMutation.isError ? confirmMutation.error : null}
                />

                {/* The full requirements/skills-alignment breakdown intentionally isn't
                    rendered here anymore — ConfirmAnalysisModal (below) is the whole "what did
                    analysis find" moment now. Analysis loading has its own state in that modal
                    too, so there's nothing to show inline while it's in flight; only a real
                    failure (which closes the modal) needs an inline home. */}
                {isConfirmed && analysisQuery.isError && (
                  <div className="rounded-2xl border border-border bg-surface p-6 sm:p-8">
                    <ErrorBanner error={analysisQuery.error} />
                  </div>
                )}

                {isConfirmed && analysisQuery.data && (
                  <>

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

      {showUnsavedDialog && (
        <UnsavedChangesDialog
          onContinueEditing={closeUnsavedDialog}
          onDiscard={discardAndContinue}
          onSave={saveAndContinue}
          saving={editJdMutation.isPending}
          error={editJdMutation.isError ? editJdMutation.error : null}
        />
      )}

      {showAnalysisModal && (analysisQuery.isLoading || analysisQuery.data) && (
        <ConfirmAnalysisModal
          status={analysisQuery.isLoading ? 'loading' : 'ready'}
          missingItems={missingAlignmentItems}
          onContinue={() => setShowAnalysisModal(false)}
        />
      )}
    </div>
  );
}
