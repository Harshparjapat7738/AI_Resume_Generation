import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AppHeader } from '@/components/layout/AppHeader';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { showToast } from '@/components/ui/toast';
import { AchievementsManager } from '@/features/profile-shared/AchievementsManager';
import { CertificationsManager } from '@/features/profile-shared/CertificationsManager';
import { ConfirmedStepNav } from '@/features/profile-shared/ConfirmedStepNav';
import { EducationManager } from '@/features/profile-shared/EducationManager';
import { ExperienceManager } from '@/features/profile-shared/ExperienceManager';
import { PersonalInfoForm } from '@/features/profile-shared/PersonalInfoForm';
import { ProjectsManager } from '@/features/profile-shared/ProjectsManager';
import { SkillsManager } from '@/features/profile-shared/SkillsManager';
import * as profileApi from '@/services/profileApi';
import { computeProfileCompletion } from '@/services/profileApi';
import { ProfileCompletionModal } from './ProfileCompletionModal';

const steps = [
  { key: 'personal', label: 'Personal' },
  { key: 'education', label: 'Education' },
  { key: 'experience', label: 'Experience' },
  { key: 'skills', label: 'Skills' },
  { key: 'projects', label: 'Projects' },
  { key: 'certifications', label: 'Certifications' },
  { key: 'achievements', label: 'Achievements' },
  { key: 'review', label: 'Review' },
] as const;

// Wizard position only — every profile record itself is already persisted server-side the
// moment it's added/edited (verified independently of this). sessionStorage (not
// localStorage) so it survives a refresh within this browsing session but doesn't linger
// indefinitely once the tab closes.
const STEP_STORAGE_KEY = 'careerforge:onboarding-step';

function readStoredStep(): number {
  const raw = window.sessionStorage.getItem(STEP_STORAGE_KEY);
  const parsed = raw === null ? NaN : Number.parseInt(raw, 10);
  return Number.isInteger(parsed) && parsed >= 0 && parsed < steps.length ? parsed : 0;
}

/**
 * Only sections profile-service actually persists appear here — every section below has a
 * real backend endpoint (see docs/API_CATALOG.md). Personal info and at least one
 * experience are practically useful for generation, so that step nudges completion; every
 * other section is genuinely optional and skippable, matching "don't force unnecessary
 * information."
 */
export function OnboardingPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [step, setStep] = useState(readStoredStep);
  const [finishStatus, setFinishStatus] = useState<'idle' | 'checking'>('idle');
  const [finishError, setFinishError] = useState<unknown>(null);
  // Set once the freshly-confirmed profile is genuinely 100% complete — renders the
  // celebration overlay in place of the rest of this page (see the bottom of this component).
  const [celebrateName, setCelebrateName] = useState<string | null>(null);
  // Guards a fast double-click landing two "Finish profile" requests before the first
  // re-render disables the button — same pattern used by ConfirmedStepNav/PersonalInfoForm.
  const finishingRef = useRef(false);

  useEffect(() => {
    window.sessionStorage.setItem(STEP_STORAGE_KEY, String(step));
  }, [step]);

  const profileQuery = useQuery({ queryKey: ['profile'], queryFn: profileApi.getProfile });

  if (profileQuery.isLoading) {
    return <FullScreenSpinner label="Loading your profile…" />;
  }
  if (profileQuery.isError || !profileQuery.data) {
    return (
      <div className="min-h-screen bg-void">
        <AppHeader />
        <main className="mx-auto max-w-2xl px-6 py-12">
          <ErrorBanner error={profileQuery.error} />
        </main>
      </div>
    );
  }

  const profile = profileQuery.data;
  const completion = computeProfileCompletion(profile);
  const updateCache = (updated: typeof profile) => queryClient.setQueryData(['profile'], updated);

  const goNext = () => setStep((s) => Math.min(s + 1, steps.length - 1));
  const goBack = () => setStep((s) => Math.max(s - 1, 0));

  const currentKey = steps[step]?.key;

  const goToDashboard = () => {
    window.sessionStorage.removeItem(STEP_STORAGE_KEY);
    navigate('/dashboard', { replace: true });
  };

  /**
   * The wizard's terminal action. Re-fetches rather than trusting the locally-cached
   * `completion` (same reasoning as ConfirmedStepNav: confirms what's actually on the server,
   * not just what this tab optimistically believes) — the celebration only ever renders once
   * that confirmed data says 100%, and nothing here navigates anywhere until this succeeds.
   */
  const handleFinish = async () => {
    if (finishingRef.current) return;
    finishingRef.current = true;
    setFinishError(null);
    setFinishStatus('checking');
    try {
      const fresh = await profileApi.getProfile();
      updateCache(fresh);
      const freshCompletion = computeProfileCompletion(fresh);
      if (freshCompletion.percentage === 100) {
        setCelebrateName(fresh.personalInformation.fullName?.trim() || 'there');
      } else {
        goToDashboard();
      }
    } catch (error) {
      setFinishError(error);
    } finally {
      setFinishStatus('idle');
      finishingRef.current = false;
    }
  };

  if (celebrateName !== null) {
    return (
      <div className="min-h-screen bg-void">
        <AppHeader />
        <ProfileCompletionModal name={celebrateName} onGoToDashboard={goToDashboard} />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-void">
      <AppHeader />
      <main className="mx-auto max-w-2xl px-6 py-12">
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium uppercase tracking-wide text-ember-soft">
            Set up your profile
          </p>
          <p className="text-xs text-ink-faint">Profile completion: {completion.percentage}%</p>
        </div>
        <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
          <div
            className="h-full rounded-full bg-linear-to-r from-ember-soft to-rose transition-all"
            style={{ width: `${completion.percentage}%` }}
          />
        </div>

        <ol className="mt-8 mb-8 flex flex-wrap items-center gap-x-2 gap-y-3 text-xs text-ink-faint">
          {steps.map((s, index) => (
            <li key={s.key} className="flex items-center gap-2">
              <span
                className={`flex h-6 w-6 items-center justify-center rounded-full border text-[11px] font-semibold transition-colors duration-300 ${
                  index === step
                    ? 'border-ember-soft text-ember-soft'
                    : index < step
                      ? 'border-mint text-mint'
                      : 'border-border text-ink-faint'
                }`}
              >
                {index < step ? '✓' : index + 1}
              </span>
              <span className={`transition-colors duration-300 ${index === step ? 'text-ink' : ''}`}>{s.label}</span>
              {index < steps.length - 1 && <span className="mx-1 h-px w-4 bg-border" aria-hidden="true" />}
            </li>
          ))}
        </ol>

        {currentKey === 'personal' && (
          <div className="animate-step-in">
            <h2 className="text-xl font-semibold text-ink">Tell us who you are</h2>
            <p className="mt-1.5 text-sm text-ink-muted">This appears at the top of everything generated.</p>
            <div className="mt-6">
              <PersonalInfoForm
                profile={profile}
                onSaved={updateCache}
                afterSave={() => {
                  showToast('✓ Saved — your data is stored.');
                  goNext();
                }}
                submitLabel="Save & continue"
              />
            </div>
            {/* Only offered while the section is genuinely empty — once it holds real data,
                "Save & continue" above is the one way forward, so a second button that skips
                straight past it (and any unsaved edits) would just be a confusing duplicate. */}
            {!completion.sections.personal && (
              <div className="mt-4 flex justify-end">
                <Button variant="ghost" onClick={goNext}>
                  Skip for now
                </Button>
              </div>
            )}
          </div>
        )}

        {currentKey === 'education' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">Education</h2>
            <p className="mt-1 text-sm text-ink-muted">Optional — add as many as you like.</p>
            <div className="mt-6">
              <EducationManager profile={profile} onChanged={updateCache} />
            </div>
            <ConfirmedStepNav
              onBack={goBack}
              onNext={goNext}
              onConfirmSaved={updateCache}
              hasData={completion.sections.education}
              verify={(p) => p.education.length > 0}
              itemNamePlural="education entries"
            />
          </div>
        )}

        {currentKey === 'experience' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">What have you actually done?</h2>
            <p className="mt-1 text-sm text-ink-muted">
              Every fact you add here gets a stable evidence ID. Generated content can only cite
              what's listed below — nothing else.
            </p>
            <div className="mt-6">
              <ExperienceManager profile={profile} onChanged={updateCache} />
            </div>
            <ConfirmedStepNav
              onBack={goBack}
              onNext={goNext}
              onConfirmSaved={updateCache}
              hasData={completion.sections.experience}
              verify={(p) => p.experiences.length > 0}
              itemNamePlural="experience entries"
            />
          </div>
        )}

        {currentKey === 'skills' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">Skills</h2>
            <p className="mt-1 text-sm text-ink-muted">Optional — languages, frameworks, tools, anything relevant.</p>
            <div className="mt-6">
              <SkillsManager profile={profile} onChanged={updateCache} />
            </div>
            <ConfirmedStepNav
              onBack={goBack}
              onNext={goNext}
              onConfirmSaved={updateCache}
              hasData={completion.sections.skills}
              verify={(p) => p.skills.length > 0}
              itemNamePlural="skills"
            />
          </div>
        )}

        {currentKey === 'projects' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">Projects</h2>
            <p className="mt-1 text-sm text-ink-muted">Optional — personal, open-source, or work projects worth citing.</p>
            <div className="mt-6">
              <ProjectsManager profile={profile} onChanged={updateCache} />
            </div>
            <ConfirmedStepNav
              onBack={goBack}
              onNext={goNext}
              onConfirmSaved={updateCache}
              hasData={completion.sections.projects}
              verify={(p) => p.projects.length > 0}
              itemNamePlural="projects"
            />
          </div>
        )}

        {currentKey === 'certifications' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">Certifications</h2>
            <p className="mt-1 text-sm text-ink-muted">Optional.</p>
            <div className="mt-6">
              <CertificationsManager profile={profile} onChanged={updateCache} />
            </div>
            <ConfirmedStepNav
              onBack={goBack}
              onNext={goNext}
              onConfirmSaved={updateCache}
              hasData={completion.sections.certifications}
              verify={(p) => p.certifications.length > 0}
              itemNamePlural="certifications"
            />
          </div>
        )}

        {currentKey === 'achievements' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">Achievements</h2>
            <p className="mt-1 text-sm text-ink-muted">Optional — awards, competitions, publications, leadership.</p>
            <div className="mt-6">
              <AchievementsManager profile={profile} onChanged={updateCache} />
            </div>
            <ConfirmedStepNav
              onBack={goBack}
              onNext={goNext}
              onConfirmSaved={updateCache}
              hasData={completion.sections.achievements}
              verify={(p) => p.achievements.length > 0}
              itemNamePlural="achievements"
            />
          </div>
        )}

        {currentKey === 'review' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">You're set up</h2>
            <p className="mt-1 text-sm text-ink-muted">
              {profile.personalInformation.fullName ?? 'Your profile'} — {completion.percentage}%
              complete: {profile.experiences.length} experience
              {profile.experiences.length === 1 ? '' : 's'}, {profile.education.length} education,{' '}
              {profile.skills.length} skill{profile.skills.length === 1 ? '' : 's'},{' '}
              {profile.projects.length} project{profile.projects.length === 1 ? '' : 's'},{' '}
              {profile.certifications.length} certification{profile.certifications.length === 1 ? '' : 's'},{' '}
              {profile.achievements.length} achievement{profile.achievements.length === 1 ? '' : 's'}. You can
              add more or edit any of this later from your profile page.
            </p>
            {finishError !== null && (
              <div className="mt-4">
                <ErrorBanner error={finishError} />
              </div>
            )}
            <div className="mt-6 flex justify-between">
              <Button variant="ghost" onClick={goBack} disabled={finishStatus === 'checking'}>
                Back
              </Button>
              <Button onClick={handleFinish} loading={finishStatus === 'checking'} disabled={finishStatus === 'checking'}>
                Finish profile
              </Button>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
