import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AppHeader } from '@/components/layout/AppHeader';
import { Card } from '@/components/ui/Card';
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

export function ProfilePage() {
  const queryClient = useQueryClient();
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

  return (
    <div className="min-h-screen bg-void">
      <AppHeader />
      <main className="mx-auto max-w-2xl px-6 py-12">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-sm font-medium uppercase tracking-wide text-ember-soft">Your profile</p>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight text-ink">
              Personal information &amp; evidence
            </h1>
          </div>
          <span className="rounded-full border border-border-strong px-3 py-1 text-xs text-ink-muted">
            {completion.percentage}% complete
          </span>
        </div>
        <p className="mt-1.5 text-sm text-ink-muted">
          This is reused for every generation — update it once, not every time.
        </p>

        <div className="mt-8 space-y-10">
          <Card>
            <h2 className="text-sm font-semibold text-ink">Personal information</h2>
            <div className="mt-4">
              <PersonalInfoForm profile={profile} onSaved={updateCache} />
            </div>
          </Card>

          <section>
            <h2 className="mb-4 text-sm font-semibold text-ink">Education</h2>
            <EducationManager profile={profile} onChanged={updateCache} />
          </section>

          <section>
            <h2 className="mb-4 text-sm font-semibold text-ink">Experience</h2>
            <ExperienceManager profile={profile} onChanged={updateCache} />
          </section>

          <section>
            <h2 className="mb-4 text-sm font-semibold text-ink">Skills</h2>
            <SkillsManager profile={profile} onChanged={updateCache} />
          </section>

          <section>
            <h2 className="mb-4 text-sm font-semibold text-ink">Projects</h2>
            <ProjectsManager profile={profile} onChanged={updateCache} />
          </section>

          <section>
            <h2 className="mb-4 text-sm font-semibold text-ink">Certifications</h2>
            <CertificationsManager profile={profile} onChanged={updateCache} />
          </section>

          <section>
            <h2 className="mb-4 text-sm font-semibold text-ink">Achievements</h2>
            <AchievementsManager profile={profile} onChanged={updateCache} />
          </section>
        </div>
      </main>
    </div>
  );
}
