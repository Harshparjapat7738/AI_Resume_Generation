import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AppHeader } from '@/components/layout/AppHeader';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { AchievementsManager } from '@/features/profile-shared/AchievementsManager';
import { CertificationsManager } from '@/features/profile-shared/CertificationsManager';
import { EducationManager } from '@/features/profile-shared/EducationManager';
import { ExperienceManager } from '@/features/profile-shared/ExperienceManager';
import { PersonalInfoForm } from '@/features/profile-shared/PersonalInfoForm';
import { ProjectsManager } from '@/features/profile-shared/ProjectsManager';
import { SkillsManager } from '@/features/profile-shared/SkillsManager';
import * as profileApi from '@/services/profileApi';
import { computeProfileCompletion } from '@/services/profileApi';

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
  const [step, setStep] = useState(0);

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
                className={`flex h-6 w-6 items-center justify-center rounded-full border text-[11px] font-semibold ${
                  index === step
                    ? 'border-ember-soft text-ember-soft'
                    : index < step
                      ? 'border-mint text-mint'
                      : 'border-border text-ink-faint'
                }`}
              >
                {index < step ? '✓' : index + 1}
              </span>
              <span className={index === step ? 'text-ink' : ''}>{s.label}</span>
              {index < steps.length - 1 && <span className="mx-1 h-px w-4 bg-border" aria-hidden="true" />}
            </li>
          ))}
        </ol>

        {currentKey === 'personal' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">Tell us who you are</h2>
            <p className="mt-1 text-sm text-ink-muted">This appears at the top of everything generated.</p>
            <div className="mt-6">
              <PersonalInfoForm profile={profile} onSaved={updateCache} submitLabel="Save & continue" />
            </div>
            <div className="mt-4 flex justify-end">
              <Button variant="ghost" onClick={goNext}>
                {completion.sections.personal ? 'Continue' : 'Skip for now'}
              </Button>
            </div>
          </div>
        )}

        {currentKey === 'education' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">Education</h2>
            <p className="mt-1 text-sm text-ink-muted">Optional — add as many as you like.</p>
            <div className="mt-6">
              <EducationManager profile={profile} onChanged={updateCache} />
            </div>
            <StepNav onBack={goBack} onNext={goNext} hasData={completion.sections.education} />
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
            <StepNav onBack={goBack} onNext={goNext} hasData={completion.sections.experience} />
          </div>
        )}

        {currentKey === 'skills' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">Skills</h2>
            <p className="mt-1 text-sm text-ink-muted">Optional — languages, frameworks, tools, anything relevant.</p>
            <div className="mt-6">
              <SkillsManager profile={profile} onChanged={updateCache} />
            </div>
            <StepNav onBack={goBack} onNext={goNext} hasData={completion.sections.skills} />
          </div>
        )}

        {currentKey === 'projects' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">Projects</h2>
            <p className="mt-1 text-sm text-ink-muted">Optional — personal, open-source, or work projects worth citing.</p>
            <div className="mt-6">
              <ProjectsManager profile={profile} onChanged={updateCache} />
            </div>
            <StepNav onBack={goBack} onNext={goNext} hasData={completion.sections.projects} />
          </div>
        )}

        {currentKey === 'certifications' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">Certifications</h2>
            <p className="mt-1 text-sm text-ink-muted">Optional.</p>
            <div className="mt-6">
              <CertificationsManager profile={profile} onChanged={updateCache} />
            </div>
            <StepNav onBack={goBack} onNext={goNext} hasData={completion.sections.certifications} />
          </div>
        )}

        {currentKey === 'achievements' && (
          <div>
            <h2 className="text-xl font-semibold text-ink">Achievements</h2>
            <p className="mt-1 text-sm text-ink-muted">Optional — awards, competitions, publications, leadership.</p>
            <div className="mt-6">
              <AchievementsManager profile={profile} onChanged={updateCache} />
            </div>
            <StepNav onBack={goBack} onNext={goNext} hasData={completion.sections.achievements} />
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
            <div className="mt-6 flex justify-between">
              <Button variant="ghost" onClick={goBack}>
                Back
              </Button>
              <Button onClick={() => navigate('/', { replace: true })}>Finish profile</Button>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

function StepNav({ onBack, onNext, hasData }: { onBack: () => void; onNext: () => void; hasData: boolean }) {
  return (
    <div className="mt-6 flex justify-between">
      <Button variant="ghost" onClick={onBack}>
        Back
      </Button>
      <Button variant={hasData ? 'primary' : 'ghost'} onClick={onNext}>
        {hasData ? 'Continue' : 'Skip'}
      </Button>
    </div>
  );
}
